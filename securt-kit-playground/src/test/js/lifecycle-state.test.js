/**
 * Pure-function harness for Playground lifecycle state transitions.
 * Run: node src/test/js/lifecycle-state.test.js
 */
const fs = require("fs");
const path = require("path");
const vm = require("vm");

const appJs = path.resolve(__dirname, "../../main/resources/support/playground/http/resources/js/app.js");
const code = fs.readFileSync(appJs, "utf8");

function stubEl() {
  return {
    textContent: "",
    className: "",
    href: "",
    value: "",
    hidden: false,
    disabled: false,
    classList: { toggle() {} },
    getAttribute() { return null; },
    setAttribute() {},
    querySelectorAll() { return []; },
    querySelector() { return stubEl(); },
    addEventListener() {},
    appendChild() {}
  };
}

const window = {
  location: { pathname: "/playground/" },
  confirm() { return true; },
  PlaygroundLifecycleState: null
};
const document = {
  getElementById() { return stubEl(); },
  querySelectorAll() { return []; },
  querySelector() { return stubEl(); },
  addEventListener() {}
};

vm.runInNewContext(code, {
  window,
  document,
  fetch() {
    return Promise.resolve({
      status: 200,
      json() { return Promise.resolve({ code: 200, data: { authEnabled: false } }); }
    });
  },
  console
});

const api = window.PlaygroundLifecycleState;
if (!api || typeof api.canRunStep !== "function") {
  throw new Error("PlaygroundLifecycleState.canRunStep was not exported");
}

function assert(cond, msg) {
  if (!cond) throw new Error(msg);
}

const idle = api.newLifecycleState("primary");
assert(api.canRunStep("insert", idle) === false, "insert blocked until ready");
idle.ready = true;
assert(api.canRunStep("insert", idle) === true, "insert allowed when ready");
assert(api.canRunStep("query", idle) === false, "query needs recordId");
idle.recordId = 7;
assert(api.canRunStep("query", idle) === true, "query allowed with recordId");
assert(api.canRunStep("update", idle) === false, "update needs previous PASSED");
idle.steps.query = "PASSED";
assert(api.canRunStep("update", idle) === true, "update after query PASSED");
assert(api.canRunStep("tamper", idle) === false, "tamper needs verify PASSED");
idle.steps.update = "PASSED";
idle.steps.queryAgain = "PASSED";
idle.steps.verify = "PASSED";
assert(api.canRunStep("tamper", idle) === true, "tamper after verify PASSED");
assert(JSON.stringify(api.order) === JSON.stringify([
  "insert", "query", "update", "queryAgain", "verify", "tamper"
]), "step order must match six-step lifecycle");

console.log("lifecycle-state.test.js OK");
process.exit(0);
