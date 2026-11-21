const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function buildValidPrefs(uid, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    displayName: "Elena",
    remindersEnabled: true,
    morningHour: 8,
    morningMinute: 30,
    eveningEnabled: true,
    eveningHour: 20,
    eveningMinute: 0,
    notificationTone: "MOTIVADOR",
    dailyGoalCount: 5,
    createdAt: now,
    updatedAt: now,
  };
}

function buildValidTask(uid, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    title: "Preparar presentación trimestral",
    notes: "Incluir métricas clave",
    category: "TRABAJO",
    urgency: "ALTA",
    timeWindow: "MANANA",
    estimatedMinutes: 45,
    priorityScore: 88,
    aiReason: "Impacto alto y ventana matutina óptima.",
    suggestedTimeLabel: "09:30",
    dueDateEpochDay: 20777,
    completed: false,
    dailyRecurring: false,
    reminderEnabled: true,
    reminderHour: 9,
    reminderMinute: 15,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write user preferences or tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(buildValidPrefs(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("t1")
      .set(buildValidTask(ALICE_UID))
  );
});

test("Owner can create, read, and update user preferences within schema bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const prefsRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(prefsRef.set(buildValidPrefs(ALICE_UID)));
  await assertSucceeds(prefsRef.get());

  // Settings update
  await assertSucceeds(
    prefsRef.update({
      morningHour: 7,
      morningMinute: 45,
      notificationTone: "COACH",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // AI summary update
  await assertSucceeds(
    prefsRef.update({
      lastAiSummary: "Enfócate primero en la presentación trimestral.",
      lastNotificationTitle: "Tu pulso de hoy",
      lastNotificationBody: "Arranca con tu tarea #1 a las 09:30.",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    prefsRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject invalid tone enum
  await assertFails(
    prefsRef.update({
      notificationTone: "INVALID",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Owner can create, list, partially update, and delete tasks; invalid payloads rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tasksCol = aliceDb.collection("users").doc(ALICE_UID).collection("tasks");

  // List empty subcollection must succeed
  await assertSucceeds(tasksCol.get());

  // Create valid task (no @DocumentId 'id' key)
  await assertSucceeds(tasksCol.doc("task_1").set(buildValidTask(ALICE_UID)));

  // Reject task with forbidden 'id' key
  await assertFails(
    tasksCol.doc("task_2").set({
      id: "task_2",
      ...buildValidTask(ALICE_UID),
    })
  );

  // Reject task with future timestamp
  await assertFails(
    tasksCol.doc("task_future").set(
      buildValidTask(ALICE_UID, new Date(Date.now() + 60000))
    )
  );

  // Toggle completion partial update succeeds
  await assertSucceeds(
    tasksCol.doc("task_1").update({
      completed: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // AI priority score partial update succeeds
  await assertSucceeds(
    tasksCol.doc("task_1").update({
      priorityScore: 95,
      aiReason: "Prioridad máxima recalculada por fecha límite.",
      suggestedTimeLabel: "09:00",
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Reject ghost field on update
  await assertFails(
    tasksCol.doc("task_1").update({
      completed: false,
      isAdminOverride: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject createdAt mutation
  await assertFails(
    tasksCol.doc("task_1").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Delete succeeds for owner
  await assertSucceeds(tasksCol.doc("task_1").delete());
});

test("Cross-user access to preferences and tasks is strictly denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(BOB_UID).set(buildValidPrefs(BOB_UID));
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("tasks")
      .doc("bob_task")
      .set(buildValidTask(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("tasks").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_task").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("tasks")
      .doc("bob_task")
      .update({ completed: true, updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_task").delete()
  );
});
