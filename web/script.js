const sampleProgram = `let Sum(A) = Psum (A,Order A )
where rec Psum (T,N) = N eq 0 -> 0
| Psum(T,N-1)+T N
in Print ( Sum (1,2,3,4,5) )`;

const routes = ["editor", "run", "tree", "tokens", "compare", "tests", "pipeline", "settings"];
const routeViews = { run: "output" };
const modeLabels = { run: "Run", ast: "AST", st: "ST" };

const appState = {
  route: "editor",
  code: localStorage.getItem("rpal-code") || sampleProgram,
  mode: localStorage.getItem("rpal-mode") || "run",
  lastFile: localStorage.getItem("rpal-file") || "Sample",
  lastRun: null,
  tokens: [],
  astTree: null,
  stTree: null,
  selectedNode: null,
  samples: [],
  selectedSample: localStorage.getItem("rpal-sample") || "",
  theme: localStorage.getItem("rpal-theme") || "light"
};

const el = {};
let mainTree = null;
let compareAstTree = null;
let compareStTree = null;
let pipelineTimer = null;
let elapsedTimer = null;
let runStartedAt = 0;

document.addEventListener("DOMContentLoaded", init);

function init() {
  [
    "pageTitle", "modeBadge", "statusBadge", "sidebar", "sidebarToggle", "themeToggle",
    "codeEditor", "lineNumbers", "editorMeta", "dirtyIndicator", "lineCount", "charCount", "lastFile",
    "fileInput", "sampleSelect", "loadSampleButton", "runSampleButton", "runButton", "tokensButton",
    "compareButton", "clearButton", "miniConsole", "stdoutBox", "stderrBox", "exitCodeBox", "causeBox",
    "runAgainButton", "copyButton", "downloadOutputButton", "treeCanvas", "treeSearchInput", "buildTreeButton",
    "fitTreeButton", "resetTreeButton", "expandTreeButton", "collapseTreeButton", "downloadSvgButton", "fullscreenTreeButton",
    "nodeLabel", "nodeType", "nodeDepth", "nodeChildren", "rawTreeText", "tokensBody", "tokenSummary", "tokenSearch", "tokenTypeFilter",
    "refreshTokensButton", "astCompareCanvas", "stCompareCanvas", "refreshCompareButton",
    "fitAstButton", "fitStButton", "testSearch", "testsList", "previewName", "previewCode", "pipelineStrip",
    "loadingOverlay", "elapsedTime", "toastHost"
  ].forEach((id) => el[id] = document.getElementById(id));

  document.body.classList.toggle("dark", appState.theme === "dark");
  el.themeToggle.textContent = appState.theme === "dark" ? "Light" : "Dark";
  el.codeEditor.value = appState.code;
  document.querySelector(`input[name="mode"][value="${appState.mode}"]`).checked = true;
  el.sampleSelect.value = appState.selectedSample;

  bindEvents();
  loadSamples();
  routeFromHash();
  renderAll();
}

function bindEvents() {
  window.addEventListener("hashchange", () => {
    routeFromHash();
    renderRoute();
  });
  document.querySelectorAll(".nav-item[data-route]").forEach((link) => {
    link.addEventListener("click", () => setRoute(link.dataset.route));
  });
  document.querySelectorAll("input[name='mode']").forEach((input) => {
    input.addEventListener("change", () => {
      appState.mode = input.value;
      persist();
      renderChrome();
    });
  });
  document.querySelectorAll("input[name='treeMode']").forEach((input) => {
    input.addEventListener("change", () => renderTreeExplorer());
  });

  el.codeEditor.addEventListener("input", () => {
    appState.code = el.codeEditor.value;
    localStorage.setItem("rpal-dirty", "true");
    el.dirtyIndicator.textContent = "Changed";
    el.dirtyIndicator.classList.add("changed");
    persist();
    renderEditorStats();
  });
  el.codeEditor.addEventListener("scroll", () => el.lineNumbers.scrollTop = el.codeEditor.scrollTop);
  el.codeEditor.addEventListener("keydown", (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
      event.preventDefault();
      runCode(appState.mode);
    }
  });

  el.runButton.addEventListener("click", () => runCode(appState.mode));
  el.runAgainButton.addEventListener("click", () => runCode(appState.mode));
  el.tokensButton.addEventListener("click", () => fetchTokens(true));
  el.compareButton.addEventListener("click", () => fetchCompare(true));
  el.refreshTokensButton.addEventListener("click", () => fetchTokens(false));
  el.refreshCompareButton.addEventListener("click", () => fetchCompare(false));
  el.clearButton.addEventListener("click", clearEditor);
  el.copyButton.addEventListener("click", copyActiveOutput);
  el.downloadOutputButton.addEventListener("click", downloadOutput);
  el.sidebarToggle.addEventListener("click", () => document.querySelector(".app-shell").classList.toggle("sidebar-collapsed"));
  el.themeToggle.addEventListener("click", toggleTheme);

  el.fileInput.addEventListener("change", loadUploadedFile);
  el.sampleSelect.addEventListener("change", () => {
    appState.selectedSample = el.sampleSelect.value;
    persist();
    previewSample(appState.selectedSample);
  });
  el.loadSampleButton.addEventListener("click", () => loadSampleToEditor(appState.selectedSample));
  el.runSampleButton.addEventListener("click", async () => {
    if (await loadSampleToEditor(appState.selectedSample)) runCode("run");
  });

  el.treeSearchInput.addEventListener("input", () => mainTree?.search(el.treeSearchInput.value));
  el.buildTreeButton.addEventListener("click", () => {
    const mode = document.querySelector("input[name='treeMode']:checked").value;
    runCode(mode);
  });
  el.fitTreeButton.addEventListener("click", () => mainTree?.fit());
  el.resetTreeButton.addEventListener("click", () => mainTree?.reset());
  el.expandTreeButton.addEventListener("click", () => mainTree?.expandAll());
  el.collapseTreeButton.addEventListener("click", () => mainTree?.collapseAll());
  el.downloadSvgButton.addEventListener("click", () => mainTree?.download("rpal-tree.svg"));
  el.fullscreenTreeButton.addEventListener("click", toggleTreeFullscreen);
  el.fitAstButton.addEventListener("click", () => compareAstTree?.fit());
  el.fitStButton.addEventListener("click", () => compareStTree?.fit());
  el.tokenSearch.addEventListener("input", renderTokens);
  el.tokenTypeFilter.addEventListener("change", renderTokens);
  el.testSearch.addEventListener("input", renderTests);
}

function setRoute(route) {
  if (!routes.includes(route)) route = "editor";
  appState.route = route;
  if (window.location.hash !== `#/${route}`) window.location.hash = `#/${route}`;
  else renderRoute();
}

function routeFromHash() {
  let raw = location.hash.replace(/^#\/?/, "");
  if (raw === "output") raw = "run";
  appState.route = routes.includes(raw) ? raw : "editor";
  if (!location.hash) history.replaceState(null, "", "#/editor");
}

function renderAll() {
  renderChrome();
  renderEditorStats();
  renderOutput();
  renderTokens();
  renderTests();
  renderRoute();
}

function renderRoute() {
  const viewRoute = routeViews[appState.route] || appState.route;
  document.querySelectorAll(".view").forEach((view) => view.classList.toggle("active", view.dataset.view === viewRoute));
  document.querySelectorAll(".nav-item").forEach((item) => item.classList.toggle("active", item.dataset.route === appState.route));
  renderChrome();
  if (appState.route === "tree") renderTreeExplorer();
  if (appState.route === "tokens") renderTokens();
  if (appState.route === "compare") renderCompare();
  if (appState.route === "tests") renderTests();
}

function renderChrome() {
  const titles = {
    editor: "Editor", run: "Run Output", tree: "Tree Explorer", tokens: "Lexer Tokens",
    compare: "AST vs ST", tests: "Tests", pipeline: "Pipeline", settings: "Settings & About"
  };
  el.pageTitle.textContent = titles[appState.route];
  el.modeBadge.textContent = `Mode: ${modeLabels[appState.mode]}`;
  const status = appState.lastRun ? (appState.lastRun.success ? "Success" : "Error") : "Ready";
  el.statusBadge.textContent = status;
  el.statusBadge.className = `badge ${appState.lastRun ? (appState.lastRun.success ? "success" : "error") : ""}`.trim();
}

function renderEditorStats() {
  const lines = Math.max(appState.code.split("\n").length, 1);
  el.lineNumbers.textContent = Array.from({ length: lines }, (_, i) => i + 1).join("\n");
  el.lineCount.textContent = lines;
  el.charCount.textContent = appState.code.length;
  el.lastFile.textContent = appState.lastFile;
  el.editorMeta.textContent = appState.lastFile;
  el.dirtyIndicator.textContent = localStorage.getItem("rpal-dirty") === "true" ? "Unsaved changes" : "Saved";
  el.dirtyIndicator.classList.toggle("changed", localStorage.getItem("rpal-dirty") === "true");
}

function renderOutput() {
  const run = appState.lastRun;
  el.stdoutBox.textContent = run?.output || "No run yet.";
  el.stderrBox.textContent = run?.error || "No errors.";
  el.exitCodeBox.textContent = run ? run.exitCode : "-";
  el.causeBox.textContent = run ? possibleCause(run.error, run.exitCode) : "Run a program to classify failures.";
  el.miniConsole.textContent = run ? `${run.success ? "Success" : "Error"} | exit ${run.exitCode} | ${run.output || run.error || "No output"}` : "No run yet.";
}

async function runCode(mode) {
  if (!appState.code.trim()) {
    appState.lastRun = { success: false, mode, output: "", error: "Enter RPAL code or upload a .rpal file.", exitCode: 1 };
    renderOutput();
    toast("Error occurred", "error");
    setRoute("run");
    return;
  }
  setBusy(true);
  animatePipeline();
  const data = await postRun(mode);
  appState.lastRun = data;
  appState.lastRun.elapsedMs = Date.now() - runStartedAt;
  if (data.tree && mode === "ast") appState.astTree = data.tree;
  if (data.tree && mode === "st") appState.stTree = data.tree;
  finishPipeline(data.success, mode);
  setBusy(false);
  localStorage.setItem("rpal-dirty", "false");
  if (data.output) localStorage.setItem("rpal-last-output", data.output);
  renderOutput();
  renderChrome();
  if (data.success && mode === "ast") toast("AST generated", "success");
  else if (data.success && mode === "st") toast("ST generated", "success");
  else if (data.success) toast("Program executed", "success");
  else toast("Error occurred", "error");
  setRoute(data.success && (mode === "ast" || mode === "st") ? "tree" : "run");
}

async function fetchTokens(navigate) {
  setBusy(true);
  const data = await postRun("tokens");
  setBusy(false);
  appState.tokens = data.tokens || [];
  appState.lastRun = data;
  renderTokens();
  renderChrome();
  toast(data.success ? "Tokens generated" : "Error occurred", data.success ? "success" : "error");
  if (navigate) setRoute("tokens");
}

async function fetchCompare(navigate) {
  setBusy(true);
  const data = await postRun("compare");
  setBusy(false);
  appState.astTree = data.astTree || appState.astTree;
  appState.stTree = data.stTree || appState.stTree;
  appState.lastRun = data;
  renderChrome();
  toast(data.success ? "Comparison generated" : "Error occurred", data.success ? "success" : "error");
  if (navigate) setRoute("compare"); else renderCompare();
}

async function postRun(mode) {
  try {
    const response = await fetch("/api/run", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ code: appState.code, mode })
    });
    return await response.json();
  } catch (error) {
    return { success: false, mode, output: "", error: error.message, exitCode: 1 };
  }
}

function renderTreeExplorer() {
  const mode = document.querySelector("input[name='treeMode']:checked").value;
  const tree = mode === "ast" ? appState.astTree : appState.stTree;
  el.rawTreeText.textContent = appState.lastRun?.output || "Raw tree text is available after running AST or ST.";
  mainTree = createTreeRenderer(el.treeCanvas, tree, {
    onSelect: (node) => {
      appState.selectedNode = node?.data || null;
      el.nodeLabel.textContent = node?.data?.label || "None selected";
      el.nodeType.textContent = node?.data?.type || "-";
      el.nodeDepth.textContent = String(node?.depth ?? "-");
      el.nodeChildren.textContent = String((node?.children || node?._children || []).length || "-");
    }
  });
}

function renderCompare() {
  compareAstTree = createTreeRenderer(el.astCompareCanvas, appState.astTree, {});
  compareStTree = createTreeRenderer(el.stCompareCanvas, appState.stTree, {});
}

function createTreeRenderer(container, treeData, options) {
  container.innerHTML = "";
  if (!window.d3 || !treeData) {
    container.innerHTML = '<div class="empty-state">No tree data yet. Run AST/ST or Compare first.</div>';
    return null;
  }

  const width = Math.max(container.getBoundingClientRect().width, 720);
  const height = Math.max(container.getBoundingClientRect().height, 460);
  const root = d3.hierarchy(JSON.parse(JSON.stringify(treeData)));
  let id = 0;
  root.each((d) => d.id = ++id);

  const svg = d3.select(container).append("svg").attr("viewBox", [0, 0, width, height]);
  const layer = svg.append("g");
  const linkLayer = layer.append("g");
  const nodeLayer = layer.append("g");
  const zoom = d3.zoom().scaleExtent([0.12, 3.5]).on("zoom", (event) => layer.attr("transform", event.transform));
  svg.call(zoom);
  let selectedId = null;

  function update(source = root) {
    const nodes = root.descendants();
    const maxLabel = Math.max(...nodes.map((d) => String(d.data.label || "").length), 8);
    const nodeWidth = Math.min(Math.max(maxLabel * 7.5 + 34, 96), 238);
    d3.tree().nodeSize([nodeWidth + 28, 96])(root);
    const visible = root.descendants();
    const minX = d3.min(visible, (d) => d.x) || 0;
    const maxX = d3.max(visible, (d) => d.x) || 0;
    const offsetX = width / 2 - (minX + maxX) / 2;
    visible.forEach((d) => { d.dx = d.x + offsetX; d.dy = d.y + 54; });

    linkLayer.selectAll("path")
      .data(root.links(), (d) => d.target.id)
      .join("path")
      .attr("class", "tree-link")
      .attr("d", d3.linkVertical().x((d) => d.dx).y((d) => d.dy));

    const node = nodeLayer.selectAll("g").data(visible, (d) => d.id);
    const enter = node.enter().append("g")
      .attr("class", (d) => nodeClassName(d))
      .on("click", (event, d) => {
        event.stopPropagation();
        selectedId = d.id;
        options.onSelect?.(d);
        update(d);
      })
      .on("dblclick", (event, d) => {
        event.stopPropagation();
        toggleNode(d);
        update(d);
      });
    enter.append("title").text((d) => `${d.data.label} (${d.data.type})`);
    enter.append("rect").attr("x", -nodeWidth / 2).attr("y", -22).attr("width", nodeWidth).attr("height", 44);
    enter.append("text").attr("text-anchor", "middle").attr("y", -2).text((d) => truncate(d.data.label));
    enter.append("text").attr("class", "node-meta").attr("text-anchor", "middle").attr("y", 14).text((d) => d.data.type);
    node.merge(enter)
      .attr("class", (d) => `${nodeClassName(d)}${d.id === selectedId ? " selected" : ""}`)
      .attr("transform", (d) => `translate(${d.dx},${d.dy})`);
    node.exit().remove();
  }

  function nodeClassName(d) {
    return `tree-node ${String(d.data.type || "operator").toLowerCase()}${d._children ? " collapsed" : ""}`;
  }
  function toggleNode(d) {
    if (d.children) { d._children = d.children; d.children = null; }
    else if (d._children) { d.children = d._children; d._children = null; }
  }
  function eachNode(fn) { root.each(fn); update(root); }
  function fit() {
    const bounds = layer.node().getBBox();
    if (!bounds.width || !bounds.height) return;
    const scale = Math.min(1.5, Math.max(0.12, Math.min((width - 70) / bounds.width, (height - 70) / bounds.height)));
    const x = width / 2 - scale * (bounds.x + bounds.width / 2);
    const y = height / 2 - scale * (bounds.y + bounds.height / 2);
    svg.transition().duration(220).call(zoom.transform, d3.zoomIdentity.translate(x, y).scale(scale));
  }
  function search(query) {
    const q = query.trim().toLowerCase();
    nodeLayer.selectAll(".tree-node").classed("search-match", (d) => q && String(d.data.label).toLowerCase().includes(q));
  }
  update(root);
  setTimeout(fit, 0);
  return {
    fit,
    reset: () => svg.transition().duration(220).call(zoom.transform, d3.zoomIdentity),
    expandAll: () => eachNode((d) => { if (d._children) { d.children = d._children; d._children = null; } }),
    collapseAll: () => eachNode((d) => { if (d.depth > 0 && d.children) { d._children = d.children; d.children = null; } }),
    search,
    download: (name) => downloadBlob(name, new XMLSerializer().serializeToString(svg.node()), "image/svg+xml;charset=utf-8")
  };
}

function renderTokens() {
  const query = el.tokenSearch.value.trim().toLowerCase();
  const typeFilter = el.tokenTypeFilter.value;
  const tokenTypes = [...new Set(appState.tokens.map((token) => token.type))].sort();
  const currentFilter = el.tokenTypeFilter.value;
  el.tokenTypeFilter.innerHTML = '<option value="">All token types</option>' + tokenTypes.map((type) => `<option value="${escapeHtml(type)}">${escapeHtml(type)}</option>`).join("");
  el.tokenTypeFilter.value = tokenTypes.includes(currentFilter) ? currentFilter : "";
  const tokens = appState.tokens.filter((token) => {
    const matchesText = !query || token.value.toLowerCase().includes(query) || token.type.toLowerCase().includes(query);
    const matchesType = !typeFilter || token.type === typeFilter;
    return matchesText && matchesType;
  });
  const counts = {
    total: appState.tokens.length,
    identifiers: appState.tokens.filter((t) => t.type === "<IDENTIFIER>").length,
    integers: appState.tokens.filter((t) => t.type === "<INTEGER>").length,
    strings: appState.tokens.filter((t) => t.type === "<STRING>").length,
    operators: appState.tokens.filter((t) => t.type === "<OPERATOR>").length,
    keywords: appState.tokens.filter((t) => !t.type.startsWith("<") && /^[a-zA-Z]+$/.test(t.type)).length
  };
  el.tokenSummary.innerHTML = Object.entries(counts).map(([label, value]) => `<div><strong>${value}</strong><span>${label}</span></div>`).join("");
  el.tokensBody.innerHTML = tokens.length
    ? tokens.map((token, i) => `<tr><td>${i}</td><td>${escapeHtml(token.value)}</td><td>${escapeHtml(token.type)}</td></tr>`).join("")
    : '<tr><td colspan="3">No tokens. Click Refresh Tokens.</td></tr>';
  el.tokensBody.querySelectorAll("tr").forEach((row) => row.addEventListener("click", () => row.classList.toggle("selected")));
}

async function loadSamples() {
  try {
    const response = await fetch("/api/samples");
    const data = await response.json();
    appState.samples = data.samples || [];
    el.sampleSelect.innerHTML = '<option value="">Select from tests/</option>' + appState.samples.map((name) => `<option value="${escapeHtml(name)}">${escapeHtml(name)}</option>`).join("");
    el.sampleSelect.value = appState.samples.includes(appState.selectedSample) ? appState.selectedSample : "";
    renderTests();
  } catch {
    appState.samples = [];
  }
}

function renderTests() {
  const q = el.testSearch.value.trim().toLowerCase();
  const samples = appState.samples.filter((name) => name.toLowerCase().includes(q));
  el.testsList.innerHTML = samples.map((name) => `
    <article class="test-card">
      <h3>${escapeHtml(name)}</h3>
      <div class="toolbar">
        <button data-action="preview" data-name="${escapeHtml(name)}">Preview</button>
        <button data-action="load" data-name="${escapeHtml(name)}">Load</button>
        <button data-action="run" data-name="${escapeHtml(name)}">Run</button>
        <button data-action="ast" data-name="${escapeHtml(name)}">AST</button>
        <button data-action="st" data-name="${escapeHtml(name)}">ST</button>
      </div>
    </article>
  `).join("") || '<div class="empty-state">No test files found.</div>';
  el.testsList.querySelectorAll("button").forEach((button) => button.addEventListener("click", () => handleTestAction(button.dataset.action, button.dataset.name)));
}

async function handleTestAction(action, name) {
  if (action === "preview") return previewSample(name);
  const loaded = await loadSampleToEditor(name);
  if (!loaded) return;
  if (action === "load") setRoute("editor");
  if (action === "run") runCode("run");
  if (action === "ast") runCode("ast");
  if (action === "st") runCode("st");
}

async function previewSample(name) {
  if (!name) return;
  const data = await fetchSample(name);
  el.previewName.textContent = name;
  el.previewCode.textContent = data.code;
}

async function loadSampleToEditor(name) {
  if (!name) return false;
  const data = await fetchSample(name);
  appState.code = data.code;
  appState.lastFile = name;
  appState.selectedSample = name;
  el.codeEditor.value = appState.code;
  el.sampleSelect.value = name;
  localStorage.setItem("rpal-dirty", "false");
  persist();
  renderEditorStats();
  toast("Sample loaded", "success");
  return true;
}

async function fetchSample(name) {
  const response = await fetch(`/api/sample?name=${encodeURIComponent(name)}`);
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "Could not load sample.");
  return data;
}

async function loadUploadedFile() {
  const file = el.fileInput.files[0];
  if (!file) return;
  if (!file.name.endsWith(".rpal")) {
    appState.lastRun = { success: false, mode: appState.mode, output: "", error: "Please upload a .rpal file.", exitCode: 1 };
    renderOutput();
    toast("Error occurred", "error");
    setRoute("run");
    return;
  }
  appState.code = await file.text();
  appState.lastFile = file.name;
  el.codeEditor.value = appState.code;
  localStorage.setItem("rpal-dirty", "false");
  persist();
  renderEditorStats();
  toast("Sample loaded", "success");
}

function clearEditor() {
  appState.code = "";
  appState.lastFile = "Untitled";
  el.codeEditor.value = "";
  localStorage.setItem("rpal-dirty", "true");
  persist();
  renderEditorStats();
}

function setBusy(busy) {
  [el.runButton, el.tokensButton, el.compareButton, el.runAgainButton, el.refreshTokensButton, el.refreshCompareButton].forEach((button) => {
    if (button) button.disabled = busy;
  });
  el.loadingOverlay.hidden = !busy;
  if (busy) {
    runStartedAt = Date.now();
    el.elapsedTime.textContent = "0.0s";
    elapsedTimer = setInterval(() => {
      el.elapsedTime.textContent = `${((Date.now() - runStartedAt) / 1000).toFixed(1)}s`;
    }, 100);
  } else {
    clearInterval(elapsedTimer);
  }
  el.statusBadge.textContent = busy ? "Running..." : "Ready";
  el.statusBadge.className = `badge ${busy ? "running" : ""}`.trim();
}

function animatePipeline() {
  clearInterval(pipelineTimer);
  const stages = [...el.pipelineStrip.querySelectorAll("span")];
  let index = 0;
  el.pipelineStrip.className = "pipeline-strip running";
  pipelineTimer = setInterval(() => {
    stages.forEach((s) => s.className = "");
    stages[index % stages.length].classList.add("active");
    index++;
  }, 160);
}

function finishPipeline(success, mode) {
  clearInterval(pipelineTimer);
  const stages = [...el.pipelineStrip.querySelectorAll("span")];
  el.pipelineStrip.className = `pipeline-strip ${success ? "success" : "error"}`;
  stages.forEach((s) => s.className = success ? "complete" : "");
  if (!success) {
    const failed = mode === "run" ? "cse" : mode === "ast" ? "parser" : "st";
    el.pipelineStrip.querySelector(`[data-stage="${failed}"]`)?.classList.add("failed");
  }
}

function possibleCause(error, exit) {
  const text = String(error || "").toLowerCase();
  if (!appState.code.trim()) return "Empty input.";
  if (text.includes("parse error") || text.includes("parsing failed")) return "Syntax error.";
  if (text.includes("division by zero") || text.includes("unbound") || text.includes("runtime")) return "Runtime error.";
  if (exit && exit !== 0) return "Interpreter exception.";
  return "No error detected.";
}

function copyActiveOutput() {
  const text = [appState.lastRun?.output, appState.lastRun?.error].filter(Boolean).join("\n\n");
  navigator.clipboard.writeText(text || "");
  toast("Output copied", "success");
}

function downloadOutput() {
  const text = [appState.lastRun?.output || "", appState.lastRun?.error || ""].join("\n\n");
  downloadBlob("rpal-output.txt", text || "No output.", "text/plain;charset=utf-8");
}

function downloadBlob(filename, text, type) {
  const blob = new Blob([text], { type });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = filename;
  link.click();
  URL.revokeObjectURL(link.href);
  if (filename.endsWith(".svg")) toast("SVG downloaded", "success");
}

function toggleTheme() {
  appState.theme = document.body.classList.toggle("dark") ? "dark" : "light";
  el.themeToggle.textContent = appState.theme === "dark" ? "Light" : "Dark";
  persist();
}

function persist() {
  localStorage.setItem("rpal-code", appState.code);
  localStorage.setItem("rpal-mode", appState.mode);
  localStorage.setItem("rpal-file", appState.lastFile);
  localStorage.setItem("rpal-theme", appState.theme);
  localStorage.setItem("rpal-sample", appState.selectedSample);
}

function toggleTreeFullscreen() {
  document.querySelector(".tree-view").classList.toggle("fullscreen-graph");
  setTimeout(() => mainTree?.fit(), 80);
}

function toast(message, type = "success") {
  const node = document.createElement("div");
  node.className = `toast ${type}`;
  node.textContent = message;
  el.toastHost.appendChild(node);
  setTimeout(() => node.remove(), 2600);
}

function truncate(value) {
  const text = String(value || "");
  return text.length > 28 ? `${text.slice(0, 25)}...` : text;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
