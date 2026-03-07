const state = {
    autoRefresh: true,
    autoRefreshHandle: null,
    jackpotSettings: [],
    editJackpotModal: null
};

function tsFormatter(value) {
    if (value === null || value === undefined) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return date.toLocaleString();
}

function yesNoBadge(value) {
    return value
        ? '<span class="badge text-bg-success">YES</span>'
        : '<span class="badge text-bg-secondary">NO</span>';
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function betBatchDetailFormatter(index, row) {
    const items = row.items || [];
    if (!items.length) {
        return '<div class="text-muted small">No items</div>';
    }

    const rows = items.map(item => `
        <tr>
            <td>${item.id}</td>
            <td>${item.seqNo}</td>
            <td>${item.boxId}</td>
            <td>${escapeHtml(item.result)}</td>
        </tr>
    `).join("");

    return `
        <div class="p-2">
            <table class="table table-sm table-bordered mb-0">
                <thead>
                <tr>
                    <th>ID</th>
                    <th>Seq</th>
                    <th>Box</th>
                    <th>Result</th>
                </tr>
                </thead>
                <tbody>${rows}</tbody>
            </table>
        </div>
    `;
}

window.tsFormatter = tsFormatter;
window.betBatchDetailFormatter = betBatchDetailFormatter;

function enabledFormatter(value) {
    return value
        ? '<span class="badge text-bg-success">ENABLED</span>'
        : '<span class="badge text-bg-secondary">DISABLED</span>';
}

function settingsActionsFormatter() {
    return `
        <button class="btn btn-sm btn-outline-primary settings-action-btn edit-settings-btn" type="button">
            Edit
        </button>
    `;
}

window.enabledFormatter = enabledFormatter;
window.settingsActionsFormatter = settingsActionsFormatter;

window.settingsActionEvents = {
    'click .edit-settings-btn': function (e, value, row) {
        openEditJackpotSettingsModal(row);
    }
};

async function fetchJson(url) {
    const response = await fetch(url, {
        headers: { "Accept": "application/json" }
    });

    if (!response.ok) {
        const text = await response.text();
        throw new Error(`${response.status} ${response.statusText}: ${text}`);
    }

    return response.json();
}

function setServerStatus(ok, text) {
    const el = document.getElementById("serverStatusText");
    el.textContent = text;
    el.className = `small ${ok ? "status-ok" : "status-bad"}`;
}

function fillCards(snapshot) {
    document.getElementById("cardSystemMode").textContent = snapshot.systemMode ?? "-";

    const pending = snapshot.pendingWin
        ? `${snapshot.pendingWin.jackpotId} / T${snapshot.pendingWin.tableId} / B${snapshot.pendingWin.boxId}`
        : "-";
    document.getElementById("cardPendingWin").textContent = pending;

    document.getElementById("cardRuby").textContent = snapshot.jackpots?.RUBY ?? "-";
    document.getElementById("cardGold").textContent = snapshot.jackpots?.GOLD ?? "-";
    document.getElementById("cardJade").textContent = snapshot.jackpots?.JADE ?? "-";
}

function fillTablesState(snapshot) {
    const tbody = document.getElementById("tablesStateBody");
    tbody.innerHTML = "";

    (snapshot.tables || []).forEach(table => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td class="mono">${table.tableId}</td>
            <td class="mono">${(table.activeBoxes || []).join(", ") || "-"}</td>
            <td class="mono">${(table.recentBoxes || []).join(", ") || "-"}</td>
            <td>${table.isActive ? '<span class="badge text-bg-success">ONLINE</span>' : '<span class="badge text-bg-secondary">OFFLINE</span>'}</td>
        `;
        tbody.appendChild(tr);
    });
}

async function refreshCurrentState() {
    const [health, snapshot] = await Promise.all([
        fetchJson("/health"),
        fetchJson("/snapshot")
    ]);

    setServerStatus(true, `OK · stateVersion=${health.stateVersion} · lastEventId=${health.lastEventId}`);
    fillCards(snapshot);
    fillTablesState(snapshot);

    document.getElementById("rawSnapshotBox").textContent = JSON.stringify(snapshot, null, 2);
}

async function refreshBetBatches() {
    const params = new URLSearchParams();
    const tableId = document.getElementById("betBatchesTableId").value.trim();
    const result = document.getElementById("betBatchesResult").value.trim();

    params.set("limit", "50");
    if (tableId) params.set("tableId", tableId);
    if (result) params.set("result", result);

    const data = await fetchJson(`/admin/bet-batches?${params.toString()}`);
    window.jQuery
        ? window.jQuery("#betBatchesTable").bootstrapTable("load", data)
        : document.getElementById("betBatchesTable").setAttribute("data", JSON.stringify(data));
}

async function refreshJackpotHits() {
    const params = new URLSearchParams();
    const tableId = document.getElementById("jackpotHitsTableId").value.trim();
    const jackpotId = document.getElementById("jackpotHitsJackpotId").value.trim();
    const status = document.getElementById("jackpotHitsStatus").value.trim();

    params.set("limit", "50");
    if (tableId) params.set("tableId", tableId);
    if (jackpotId) params.set("jackpotId", jackpotId);
    if (status) params.set("status", status);

    const data = await fetchJson(`/admin/jackpot-hits?${params.toString()}`);
    window.jQuery
        ? window.jQuery("#jackpotHitsTable").bootstrapTable("load", data)
        : document.getElementById("jackpotHitsTable").setAttribute("data", JSON.stringify(data));
}

async function refreshPendingWins() {
    const params = new URLSearchParams();
    const tableId = document.getElementById("pendingWinsTableId").value.trim();
    const dealerConfirmed = document.getElementById("pendingWinsDealerConfirmed").value.trim();

    if (tableId) params.set("tableId", tableId);
    if (dealerConfirmed) params.set("dealerConfirmed", dealerConfirmed);

    const query = params.toString();
    const data = await fetchJson(`/admin/pending-wins${query ? `?${query}` : ""}`);
    window.jQuery
        ? window.jQuery("#pendingWinsTable").bootstrapTable("load", data)
        : document.getElementById("pendingWinsTable").setAttribute("data", JSON.stringify(data));
}

async function refreshJackpotSettings() {
    const data = await fetchJson("/admin/settings/jackpots");
    state.jackpotSettings = data;

    if (window.jQuery) {
        window.jQuery("#jackpotSettingsTable").bootstrapTable("load", data);
    }
}

async function refreshDashboard() {
    const dashboard = await fetchJson("/admin/dashboard");

    document.getElementById("dashboardSystemMode").textContent = dashboard.systemMode ?? "-";
    document.getElementById("dashboardActiveTables").textContent = `${dashboard.activeTablesCount} / ${dashboard.totalTables}`;
    document.getElementById("dashboardBatchesToday").textContent = dashboard.totalBatchesToday ?? 0;
    document.getElementById("dashboardHitsToday").textContent = dashboard.totalHitsToday ?? 0;

    document.getElementById("dashboardRuby").textContent = dashboard.jackpots?.RUBY ?? "-";
    document.getElementById("dashboardGold").textContent = dashboard.jackpots?.GOLD ?? "-";
    document.getElementById("dashboardJade").textContent = dashboard.jackpots?.JADE ?? "-";

    const pendingWinBox = document.getElementById("dashboardPendingWinBox");
    if (dashboard.pendingWin) {
        pendingWinBox.innerHTML = `
            <div class="mb-2"><strong>Jackpot:</strong> ${escapeHtml(dashboard.pendingWin.jackpotId)}</div>
            <div class="mb-2"><strong>Table:</strong> ${dashboard.pendingWin.tableId}</div>
            <div class="mb-2"><strong>Winning Box:</strong> ${dashboard.pendingWin.winningBoxId}</div>
            <div class="mb-2"><strong>Amount:</strong> ${dashboard.pendingWin.winAmount}</div>
            <div><strong>Dealer Confirmed:</strong> ${yesNoBadge(dashboard.pendingWin.dealerConfirmed)}</div>
        `;
    } else {
        pendingWinBox.innerHTML = `<div class="text-muted">No pending win</div>`;
    }

    const latestHitBox = document.getElementById("dashboardLatestHitBox");
    if (dashboard.latestHit) {
        latestHitBox.innerHTML = `
            <div class="mb-2"><strong>Jackpot:</strong> ${escapeHtml(dashboard.latestHit.jackpotId)}</div>
            <div class="mb-2"><strong>Table:</strong> ${dashboard.latestHit.tableId}</div>
            <div class="mb-2"><strong>Trigger Box:</strong> ${dashboard.latestHit.triggerBoxId}</div>
            <div class="mb-2"><strong>Amount:</strong> ${dashboard.latestHit.winAmount}</div>
            <div class="mb-2"><strong>Status:</strong> ${escapeHtml(dashboard.latestHit.status)}</div>
            <div><strong>Hit At:</strong> ${tsFormatter(dashboard.latestHit.hitAt)}</div>
        `;
    } else {
        latestHitBox.innerHTML = `<div class="text-muted">No hits yet</div>`;
    }

    const latestBatchesBody = document.getElementById("dashboardLatestBatchesBody");
    latestBatchesBody.innerHTML = (dashboard.latestBatches || []).map(batch => `
        <tr>
            <td class="mono">${batch.id}</td>
            <td>${batch.tableId}</td>
            <td>${tsFormatter(batch.confirmedAt)}</td>
            <td>${escapeHtml(batch.result ?? "")}</td>
            <td>${escapeHtml(batch.winningJackpotId ?? "-")}</td>
            <td>${batch.winningBoxId ?? "-"}</td>
        </tr>
    `).join("");
}

async function refreshAll() {
    try {
        await Promise.all([
            refreshDashboard(),
            refreshCurrentState(),
            refreshBetBatches(),
            refreshJackpotHits(),
            refreshPendingWins(),
            refreshJackpotSettings
        ]);
    } catch (e) {
        console.error(e);
        setServerStatus(false, `ERROR · ${e.message}`);
    }
}

async function saveJackpotSettings(event) {
    event.preventDefault();
    resetSettingsMessages();

    const jackpotId = document.getElementById("settingsJackpotId").value;
    const resetAmount = Number(document.getElementById("settingsResetAmount").value);
    const contributionPerBet = Number(document.getElementById("settingsContributionPerBet").value);
    const hitFrequencyGames = Number(document.getElementById("settingsHitFrequencyGames").value);
    const priorityOrder = Number(document.getElementById("settingsPriorityOrder").value);
    const enabled = document.getElementById("settingsEnabled").checked;

    const payload = {
        resetAmount,
        contributionPerBet,
        hitFrequencyGames,
        priorityOrder,
        enabled
    };

    const saveBtn = document.getElementById("saveJackpotSettingsBtn");
    saveBtn.disabled = true;

    try {
        const response = await fetch(`/admin/settings/jackpots/${encodeURIComponent(jackpotId)}`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json"
            },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || `HTTP ${response.status}`);
        }

        document.getElementById("settingsSaveSuccess").classList.remove("d-none");

        await refreshJackpotSettings();
        await refreshCurrentState();

        setTimeout(() => {
            state.editJackpotModal.hide();
        }, 500);
    } catch (e) {
        const errorEl = document.getElementById("settingsSaveError");
        errorEl.textContent = e.message || "Failed to save settings";
        errorEl.classList.remove("d-none");
    } finally {
        saveBtn.disabled = false;
    }
}

function resetSettingsMessages() {
    document.getElementById("settingsSaveError").classList.add("d-none");
    document.getElementById("settingsSaveSuccess").classList.add("d-none");
    document.getElementById("settingsSaveError").textContent = "";
}

function openEditJackpotSettingsModal(row) {
    resetSettingsMessages();

    document.getElementById("settingsJackpotId").value = row.jackpotId;
    document.getElementById("settingsJackpotIdReadonly").value = row.jackpotId;
    document.getElementById("settingsResetAmount").value = row.resetAmount;
    document.getElementById("settingsContributionPerBet").value = row.contributionPerBet;
    document.getElementById("settingsHitFrequencyGames").value = row.hitFrequencyGames;
    document.getElementById("settingsPriorityOrder").value = row.priorityOrder;
    document.getElementById("settingsEnabled").checked = !!row.enabled;

    state.editJackpotModal.show();
}

function resetSettingsMessages() {
    document.getElementById("settingsSaveError").classList.add("d-none");
    document.getElementById("settingsSaveSuccess").classList.add("d-none");
    document.getElementById("settingsSaveError").textContent = "";
}

function openEditJackpotSettingsModal(row) {
    resetSettingsMessages();

    document.getElementById("settingsJackpotId").value = row.jackpotId;
    document.getElementById("settingsJackpotIdReadonly").value = row.jackpotId;
    document.getElementById("settingsResetAmount").value = row.resetAmount;
    document.getElementById("settingsContributionPerBet").value = row.contributionPerBet;
    document.getElementById("settingsHitFrequencyGames").value = row.hitFrequencyGames;
    document.getElementById("settingsPriorityOrder").value = row.priorityOrder;
    document.getElementById("settingsEnabled").checked = !!row.enabled;

    state.editJackpotModal.show();
}

function initTables() {
    if (!window.jQuery) {
        console.warn("Bootstrap Table expects jQuery-style plugin bridge; table load fallback is limited.");
        return;
    }

    window.jQuery("#betBatchesTable").bootstrapTable({ data: [] });
    window.jQuery("#jackpotHitsTable").bootstrapTable({ data: [] });
    window.jQuery("#pendingWinsTable").bootstrapTable({ data: [] });
    window.jQuery("#jackpotSettingsTable").bootstrapTable({ data: [] });
}

function setupFilters() {
    document.getElementById("betBatchesFilterForm").addEventListener("submit", async (e) => {
        e.preventDefault();
        await refreshBetBatches();
    });

    document.getElementById("jackpotHitsFilterForm").addEventListener("submit", async (e) => {
        e.preventDefault();
        await refreshJackpotHits();
    });

    document.getElementById("pendingWinsFilterForm").addEventListener("submit", async (e) => {
        e.preventDefault();
        await refreshPendingWins();
    });
}

function setupSettingsUi() {
    const modalElement = document.getElementById("editJackpotSettingsModal");
    state.editJackpotModal = new bootstrap.Modal(modalElement);

    document.getElementById("editJackpotSettingsForm")
        .addEventListener("submit", saveJackpotSettings);

    document.getElementById("refreshSettingsBtn")
        .addEventListener("click", async () => {
            await refreshJackpotSettings();
        });
}

function setupToolbar() {
    document.getElementById("refreshDashboardBtn")?.addEventListener("click", async () => {
        await refreshDashboard();
    });

    document.getElementById("refreshAllBtn").addEventListener("click", async () => {
        await refreshAll();
    });

    const autoBtn = document.getElementById("autoRefreshBtn");
    autoBtn.addEventListener("click", () => {
        state.autoRefresh = !state.autoRefresh;
        autoBtn.textContent = `Auto: ${state.autoRefresh ? "ON" : "OFF"}`;

        if (state.autoRefresh) {
            startAutoRefresh();
        } else {
            stopAutoRefresh();
        }
    });

    document.getElementById("clearLiveEventsBtn")?.addEventListener("click", () => {
        liveEvents.length = 0;
        document.getElementById("liveEventsBody").innerHTML = "";
    });
}

function startAutoRefresh() {
    stopAutoRefresh();
    state.autoRefreshHandle = setInterval(() => {
        refreshAll();
    }, 3000);
}

function stopAutoRefresh() {
    if (state.autoRefreshHandle) {
        clearInterval(state.autoRefreshHandle);
        state.autoRefreshHandle = null;
    }
}

let ws = null;
const liveEvents = [];
const MAX_LIVE_EVENTS = 200;

function appendLiveEventRow(msg) {
    liveEvents.unshift(msg);
    if (liveEvents.length > MAX_LIVE_EVENTS) {
        liveEvents.length = MAX_LIVE_EVENTS;
    }

    const tbody = document.getElementById("liveEventsBody");
    tbody.innerHTML = liveEvents.map(event => `
        <tr>
            <td class="mono">${event.eventId ?? ""}</td>
            <td>${tsFormatter(event.ts)}</td>
            <td><span class="badge text-bg-primary">${escapeHtml(event.eventType ?? event.type)}</span></td>
            <td class="mono">${event.stateVersion ?? ""}</td>
            <td><code>${escapeHtml(event.payloadJson ?? event.message ?? "")}</code></td>
        </tr>
    `).join("");
}

function connectWs() {
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    const url = `${protocol}//${location.host}/admin/ws`;

    ws = new WebSocket(url);

    ws.onopen = () => {
        console.log("Admin WS connected");
        setServerStatus(true, "WS connected");
    };

    ws.onmessage = async (event) => {
        try {
            const msg = JSON.parse(event.data);
            console.log("WS message", msg);

            if (msg.type === "connected" || msg.type === "pong") {
                appendLiveEventRow(msg);
                return;
            }

            if (msg.type === "event") {
                appendLiveEventRow(msg);

                await Promise.all([
                    refreshDashboard(),
                    refreshCurrentState(),
                    refreshBetBatches(),
                    refreshJackpotHits(),
                    refreshPendingWins(),
                    refreshJackpotSettings
                ]);
            }
        } catch (e) {
            console.error("Failed to parse WS message", e);
        }
    };

    ws.onclose = () => {
        console.log("Admin WS closed");
        setServerStatus(false, "WS disconnected, reconnecting...");
        setTimeout(connectWs, 2000);
    };

    ws.onerror = (e) => {
        console.error("WS error", e);
    };
}

document.addEventListener("DOMContentLoaded", async () => {
    initTables();
    setupFilters();
    setupToolbar();
    setupSettingsUi();
    connectWs();
    await refreshAll();
    startAutoRefresh();
});