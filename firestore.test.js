const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_001";
const ADMIN_EMAIL = "mdforied30@gmail.com";
const WORKER_A_UID = "worker_alice";
const WORKER_B_UID = "worker_bob";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

function validWorkerPayload(uid, overrides = {}) {
  const now = pastDate();
  return {
    userId: uid,
    name: "Alice Johnson",
    email: "alice@fieldpulse.test",
    phone: "+1-555-0101",
    department: "Electrical Inspection",
    role: "worker",
    locationGranted: false,
    backgroundLocationGranted: false,
    cameraGranted: false,
    micGranted: false,
    permissionStatus: "PENDING",
    serviceRunning: false,
    activeCameraFacing: "BACK",
    requestedCameraFacing: "BACK",
    cameraStreamEnabled: false,
    micStreamEnabled: false,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("Unauthenticated users are blocked from all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("workers").get());
  await assertFails(unauthDb.collection("tasks").get());
  await assertFails(
    unauthDb.collection("workers").doc(WORKER_A_UID).set(validWorkerPayload(WORKER_A_UID))
  );
});

test("Worker registration enforces ownership, forbids role escalation, and rejects ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(WORKER_A_UID, {
    email: "alice@fieldpulse.test",
    email_verified: true,
  }).firestore();

  // Valid worker creation succeeds
  await assertSucceeds(
    aliceDb.collection("workers").doc(WORKER_A_UID).set(validWorkerPayload(WORKER_A_UID))
  );

  // Self-assigned admin role fails
  await assertFails(
    aliceDb.collection("workers").doc(WORKER_A_UID + "_esc").set(
      validWorkerPayload(WORKER_A_UID + "_esc", { role: "admin" })
    )
  );

  // Ghost / @DocumentId id field fails
  await assertFails(
    aliceDb.collection("workers").doc(WORKER_A_UID + "_id").set(
      validWorkerPayload(WORKER_A_UID + "_id", { id: "bad_id" })
    )
  );
});

test("Email spoofing attack with unverified email is rejected for admin actions", async () => {
  const spoofAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("workers").doc(WORKER_A_UID).set(validWorkerPayload(WORKER_A_UID));
  });

  // Unverified email spoofing admin cannot list all workers or create tasks
  await assertFails(spoofAdminDb.collection("workers").get());
  await assertFails(
    spoofAdminDb.collection("tasks").doc("task_spoof").set({
      title: "Spoofed Task",
      description: "Attempted hijack",
      assignedWorkerId: WORKER_A_UID,
      assignedWorkerName: "Alice Johnson",
      createdBy: "spoof_uid",
      priority: "HIGH",
      status: "PENDING",
      locationLabel: "Sector 7",
      workerNotes: "",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Worker permission instant sync & telemetry streaming succeed; cross-user access denied", async () => {
  const aliceDb = testEnv.authenticatedContext(WORKER_A_UID, {
    email: "alice@fieldpulse.test",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(WORKER_B_UID, {
    email: "bob@fieldpulse.test",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    aliceDb.collection("workers").doc(WORKER_A_UID).set(validWorkerPayload(WORKER_A_UID))
  );

  // Instant sync of ALL_GRANTED + GPS coordinates succeeds
  await assertSucceeds(
    aliceDb.collection("workers").doc(WORKER_A_UID).update({
      locationGranted: true,
      backgroundLocationGranted: true,
      cameraGranted: true,
      micGranted: true,
      permissionStatus: "ALL_GRANTED",
      serviceRunning: true,
      latitude: 37.7749,
      longitude: -122.4194,
      accuracyMeters: 4.5,
      speedMps: 1.2,
      updatedAt: pastDate(500),
    })
  );

  // Worker cannot modify admin-controlled requestedCameraFacing
  await assertFails(
    aliceDb.collection("workers").doc(WORKER_A_UID).update({
      requestedCameraFacing: "FRONT",
      updatedAt: pastDate(500),
    })
  );

  // Worker cannot read or write another worker's profile or telemetry
  await assertFails(bobDb.collection("workers").doc(WORKER_A_UID).get());
  await assertFails(bobDb.collection("workers").get());
  await assertSucceeds(bobDb.collection("workers").where("userId", "==", WORKER_B_UID).get());

  // Live telemetry subcollection write/read
  await assertSucceeds(
    aliceDb.collection("workers").doc(WORKER_A_UID).collection("telemetry").doc("live").set({
      userId: WORKER_A_UID,
      cameraFrameBase64: "dGVzdGZyYW1l",
      cameraFacing: "BACK",
      audioLevelDb: -18.5,
      audioBars: [0.2, 0.4, 0.7, 0.5],
      audioChunkBase64: "AQIDBA==",
      streamingVideo: true,
      streamingAudio: true,
      updatedAt: pastDate(500),
    })
  );

  // Location & Media logs
  await assertSucceeds(
    aliceDb.collection("workers").doc(WORKER_A_UID).collection("locationLogs").doc("loc_1").set({
      userId: WORKER_A_UID,
      latitude: 37.7749,
      longitude: -122.4194,
      accuracyMeters: 5.0,
      speedMps: 0.8,
      createdAt: pastDate(500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("workers").doc(WORKER_A_UID).collection("mediaLogs").doc("med_1").set({
      userId: WORKER_A_UID,
      taskId: "task_1",
      note: "Panel inspection verified",
      mediaType: "PHOTO",
      thumbnailBase64: "cHJvb2Y=",
      latitude: 37.7749,
      longitude: -122.4194,
      createdAt: pastDate(500),
    })
  );
  await assertFails(bobDb.collection("workers").doc(WORKER_A_UID).collection("locationLogs").get());
});

test("Admin Live Monitor reads all workers, toggles requestedCameraFacing, and manages tasks with terminal lock", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(WORKER_A_UID, {
    email: "alice@fieldpulse.test",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    aliceDb.collection("workers").doc(WORKER_A_UID).set(validWorkerPayload(WORKER_A_UID))
  );

  // Admin lists all workers and switches worker's requested camera facing
  await assertSucceeds(adminDb.collection("workers").get());
  await assertSucceeds(
    adminDb.collection("workers").doc(WORKER_A_UID).update({
      requestedCameraFacing: "FRONT",
      updatedAt: pastDate(500),
    })
  );

  // Admin creates task assigned to Alice
  await assertSucceeds(
    adminDb.collection("tasks").doc("task_1").set({
      title: "Inspect Substation B",
      description: "Check thermal readings and breaker load.",
      assignedWorkerId: WORKER_A_UID,
      assignedWorkerName: "Alice Johnson",
      createdBy: ADMIN_UID,
      priority: "URGENT",
      status: "PENDING",
      locationLabel: "North Substation",
      workerNotes: "",
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );

  // Worker filtered query succeeds; unfiltered query fails
  await assertSucceeds(
    aliceDb.collection("tasks").where("assignedWorkerId", "==", WORKER_A_UID).get()
  );
  await assertFails(aliceDb.collection("tasks").get());

  // Worker starts and completes task
  await assertSucceeds(
    aliceDb.collection("tasks").doc("task_1").update({
      status: "IN_PROGRESS",
      workerNotes: "Arrived on site",
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("tasks").doc("task_1").update({
      status: "COMPLETED",
      workerNotes: "All breakers nominal",
      updatedAt: pastDate(500),
    })
  );

  // Terminal state lock: worker cannot edit COMPLETED task
  await assertFails(
    aliceDb.collection("tasks").doc("task_1").update({
      status: "IN_PROGRESS",
      workerNotes: "Reopening",
      updatedAt: pastDate(200),
    })
  );

  // Admin can override/reopen completed task
  await assertSucceeds(
    adminDb.collection("tasks").doc("task_1").update({
      status: "IN_PROGRESS",
      updatedAt: pastDate(100),
    })
  );
});
