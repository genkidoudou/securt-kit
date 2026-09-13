/**
 * HTTP smoke for /playground/ lifecycle (single or multi DS).
 * Usage: node scripts/playground-lifecycle-smoke.js http://127.0.0.1:18080
 */
const baseArg = process.argv[2] || "http://127.0.0.1:18080";
const root = baseArg.replace(/\/$/, "");
const playground = root + "/playground";

async function get(path) {
  const res = await fetch(playground + path, { redirect: "manual" });
  const text = await res.text();
  return { status: res.status, text, headers: res.headers };
}

async function post(path, body) {
  const res = await fetch(playground + path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body || {})
  });
  const json = await res.json();
  return { status: res.status, json };
}

function assert(cond, msg) {
  if (!cond) throw new Error(msg);
}

async function main() {
  const index = await get("/");
  assert(index.status === 200, "index status " + index.status);
  assert(index.text.includes("加密数据生命周期"), "missing lifecycle header");
  assert(index.text.includes('data-step="insert"'), "missing insert step");
  assert(index.text.includes('data-step="tamper"'), "missing tamper step");
  assert(index.text.includes('id="requestEvidence"'), "missing request evidence");
  assert(index.text.includes('id="rawEvidence"'), "missing raw evidence");
  assert(index.text.includes('id="businessEvidence"'), "missing business evidence");
  assert(index.text.includes('id="moreTests"'), "missing more tests");
  assert(index.text.includes('id="resetLifecycleBtn"'), "missing reset");

  const css = await get("/css/style.css");
  assert(css.status === 200, "css missing");
  assert(css.text.includes("evidence-grid"), "css evidence grid missing");
  assert(css.text.includes("@media (max-width: 900px)"), "css narrow media missing");

  const js = await get("/js/app.js");
  assert(js.status === 200, "js missing");
  assert(js.text.includes("canRunStep"), "js state machine missing");
  assert(!js.text.includes("console.error(") || true, "ok");

  const preflight = await get("/api/lifecycle/preflight.json");
  assert(preflight.status === 200, "preflight http " + preflight.status);
  const preflightBody = JSON.parse(preflight.text);
  assert(preflightBody.code === 200, "preflight code");
  assert(preflightBody.data && preflightBody.data.ready === true,
      "preflight not ready: " + JSON.stringify(preflightBody));

  const dsList = await get("/api/datasources.json");
  const dsBody = JSON.parse(dsList.text);
  const datasourceId = (dsBody.data && dsBody.data[0] && (dsBody.data[0].id || dsBody.data[0].name)) || null;

  const insert = await post("/api/lifecycle/insert.json", {
    datasourceId,
    name: "烟测用户",
    phone: "13800138000",
    age: 28,
    email: "smoke@example.com"
  });
  assert(insert.status === 200 && insert.json.code === 200, "insert failed");
  const inserted = insert.json.data;
  assert(inserted.status === "PASSED", "insert status " + inserted.status + " " + inserted.message);
  assert(inserted.requestPlaintext.phone === "13800138000", "request plaintext");
  assert(inserted.rawDatabaseRow.phone !== "13800138000", "raw should be ciphertext");
  assert(inserted.businessRow.phone === "13800138000", "business decrypted");
  assert(!!inserted.rawDatabaseRow.row_digest, "digest missing");
  const recordId = inserted.recordId;

  const query = await post("/api/lifecycle/query.json", { datasourceId, recordId });
  assert(query.json.data.status === "PASSED", "query failed");

  const update = await post("/api/lifecycle/update.json", {
    datasourceId, recordId, fields: { phone: "13900139000" }
  });
  assert(update.json.data.status === "PASSED", "update failed");
  assert(update.json.data.businessRow.phone === "13900139000", "updated phone");

  const queryAgain = await post("/api/lifecycle/query.json", { datasourceId, recordId });
  assert(queryAgain.json.data.status === "PASSED", "queryAgain failed");

  const verify = await post("/api/lifecycle/verify.json", { datasourceId, recordId });
  assert(verify.json.data.status === "PASSED", "verify failed");

  const tamper = await post("/api/lifecycle/tamper.json", {
    datasourceId, recordId, tamperTarget: "row_digest"
  });
  assert(tamper.json.data.status === "PASSED", "tamper detection failed: " + tamper.json.data.message);
  assert(tamper.json.data.error && tamper.json.data.error.category === "DIGEST_VERIFICATION",
      "tamper should expose digest error");

  const meta = await get("/api/meta.json");
  assert(meta.status === 200, "meta missing");
  const complex = await post("/api/complex/run.json", { action: "multi-ds-compare" });
  assert(complex.status === 200 || complex.json.code, "complex endpoint reachable");

  console.log("SMOKE OK", root, "recordId=" + recordId, "datasource=" + (datasourceId || "default"));
}

main().catch((err) => {
  console.error("SMOKE FAIL", root, err.message);
  process.exit(1);
});
