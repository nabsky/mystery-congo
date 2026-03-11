const state = {
    autoRefresh: true,
    autoRefreshHandle: null,
    editJackpotModal: null,
    jackpotSettings: [],
    serverSettings: null
};

let ws = null;
const liveEvents = [];
const MAX_LIVE_EVENTS = 200;

function formatMoney(v, currency = "") {
    if (v === null || v === undefined) return "-";
    const formatted = Number(v).toLocaleString("fr-FR");
    return currency ? `${formatted} ${currency}` : formatted;
}

function formatDelta(v) {
    if (v === null || v === undefined) return "";
    if (v === 0) return "";
    const sign = v > 0 ? "+" : "-";
    const formatted = Math.abs(Number(v)).toLocaleString("fr-FR");
    return `(${sign}${formatted})`;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

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

function enabledFormatter(value) {
    return value
        ? '<span class="badge text-bg-success">ENABLED</span>'
        : '<span class="badge text-bg-secondary">DISABLED</span>';
}

function boxIdsFormatter(value) {
    if (!Array.isArray(value) || !value.length) return "-";
    return value.join(", ");
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

function settingsActionsFormatter() {
    return `
        <button class="btn btn-sm btn-outline-primary edit-settings-btn" type="button">
            Edit
        </button>
    `;
}

window.tsFormatter = tsFormatter;
window.betBatchDetailFormatter = betBatchDetailFormatter;
window.enabledFormatter = enabledFormatter;
window.settingsActionsFormatter = settingsActionsFormatter;
window.boxIdsFormatter = boxIdsFormatter;

window.settingsActionEvents = {
    "click .edit-settings-btn": function (e, value, row) {
        openEditJackpotSettingsModal(row);
    }
};

async function fetchJson(url) {
    const response = await fetch(url, {
        headers: { Accept: "application/json" }
    });

    if (!response.ok) {
        const text = await response.text();
        throw new Error(`${response.status} ${response.statusText}: ${text}`);
    }

    return response.json();
}

function setServerStatus(ok, text) {
    const el = document.getElementById("serverStatusText");
    if (!el) return;
    el.textContent = text;
    el.className = `small ${ok ? "status-ok" : "status-bad"}`;
}

function renderProbability(draws, frequency) {
    if (draws == null || !frequency) return "-";
    const probability = Math.min(1, draws / frequency);
    return `${Math.round(probability * 100)}%`;
}

function renderExpectedHit(draws, frequency) {
    if (draws == null || !frequency) return "-";
    const remaining = frequency - draws;
    if (remaining <= 0) return "any moment";
    return `~${Number(remaining).toLocaleString("fr-FR")} draws`;
}

function renderJackpotProgress(elementId, draws, frequency) {
    const el = document.getElementById(elementId);
    if (!el) return;

    if (draws == null || !frequency) {
        el.style.width = "0%";
        el.classList.remove("jackpot-hot-bar");
        return;
    }

    const ratio = draws / frequency;
    const visibleRatio = Math.min(1, ratio);

    el.style.width = `${visibleRatio * 100}%`;
    el.classList.remove("jackpot-hot-bar");

    if (ratio >= 1.2) {
        el.classList.add("jackpot-hot-bar");
    }
}

function renderSystemStatus(snapshot) {
    const modeEl = document.getElementById("cardSystemMode");
    const detailsEl = document.getElementById("cardSystemStatusDetails");
    const cardEl = document.getElementById("systemStatusCard");
    if (!modeEl || !detailsEl || !cardEl) return;

    const currency = snapshot.currencyCode ?? "";
    const pending = snapshot.pendingWin;

    cardEl.classList.remove("ruby-glow", "gold-glow", "jade-glow");

    if (!pending) {
        modeEl.textContent = "ACCEPTING_BETS";
        detailsEl.textContent = "No pending win";
        return;
    }

    modeEl.textContent = "PENDING_WIN";
    detailsEl.textContent =
        `${pending.jackpotId} / T${pending.tableId} / B${pending.boxId} / ${formatMoney(pending.winAmount, currency)}`;

    if (pending.jackpotId === "RUBY") cardEl.classList.add("ruby-glow");
    if (pending.jackpotId === "GOLD") cardEl.classList.add("gold-glow");
    if (pending.jackpotId === "JADE") cardEl.classList.add("jade-glow");
}

function renderJackpotCard(snapshot, key, ids) {
    const info = snapshot.jackpots?.[key];
    const growth = snapshot.jackpotGrowth?.[key];
    const settings = snapshot.jackpotSettings?.[key];
    const currency = snapshot.currencyCode ?? "";

    const labelEl = document.getElementById(ids.label);
    const valueEl = document.getElementById(ids.value);
    const drawsEl = document.getElementById(ids.draws);
    const probabilityEl = document.getElementById(ids.probability);
    const expectedHitEl = document.getElementById(ids.expectedHit);
    const containerEl = document.getElementById(ids.container);

    if (labelEl) {
        labelEl.textContent = `${key} ${formatDelta(growth)}`.trim();
    }

    if (valueEl) {
        valueEl.textContent = formatMoney(info?.currentAmount, currency);
    }

    if (drawsEl) {
        drawsEl.textContent = info?.gamesSinceLastHit != null
            ? `${Number(info.gamesSinceLastHit).toLocaleString("fr-FR")}`
            : "-";
    }

    if (probabilityEl) {
        probabilityEl.textContent = renderProbability(
            info?.gamesSinceLastHit,
            settings?.hitFrequencyGames
        );
    }

    if (expectedHitEl) {
        expectedHitEl.textContent = renderExpectedHit(
            info?.gamesSinceLastHit,
            settings?.hitFrequencyGames
        );
    }

    renderJackpotProgress(
        ids.progress,
        info?.gamesSinceLastHit,
        settings?.hitFrequencyGames
    );

    if (containerEl) {
        containerEl.classList.remove("ruby-glow", "gold-glow", "jade-glow");

        if (snapshot.pendingWin?.jackpotId === key) {
            if (key === "RUBY") containerEl.classList.add("ruby-glow");
            if (key === "GOLD") containerEl.classList.add("gold-glow");
            if (key === "JADE") containerEl.classList.add("jade-glow");
        }
    }
}

function fillCards(snapshot) {
    renderSystemStatus(snapshot);

    renderJackpotCard(snapshot, "RUBY", {
        label: "cardRubyLabel",
        value: "cardRuby",
        draws: "cardRubyDraws",
        probability: "rubyProbability",
        expectedHit: "rubyExpectedHit",
        progress: "rubyProgress",
        container: "cardRubyContainer"
    });

    renderJackpotCard(snapshot, "GOLD", {
        label: "cardGoldLabel",
        value: "cardGold",
        draws: "cardGoldDraws",
        probability: "goldProbability",
        expectedHit: "goldExpectedHit",
        progress: "goldProgress",
        container: "cardGoldContainer"
    });

    renderJackpotCard(snapshot, "JADE", {
        label: "cardJadeLabel",
        value: "cardJade",
        draws: "cardJadeDraws",
        probability: "jadeProbability",
        expectedHit: "jadeExpectedHit",
        progress: "jadeProgress",
        container: "cardJadeContainer"
    });
}

function fillTablesState(snapshot) {
    const tbody = document.getElementById("tablesStateBody");
    if (!tbody) return;

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

function renderMiniTableStage(snapshot) {
    const root = document.getElementById("miniTableStage");
    if (!root) return;

    const tables = snapshot.tables || [];
    root.innerHTML = "";

    const pending = snapshot.pendingWin;
    const winnerTableId = pending?.tableId ?? null;
    const winnerBoxId = pending?.boxId ?? null;
    const winnerJackpotId = pending?.jackpotId ?? null;

    tables.forEach((table, index) => {
        const tableEl = document.createElement("div");
        tableEl.className = "mini-table";

        const labelEl = document.createElement("div");
        labelEl.className = "mini-table-label";
        labelEl.textContent = table.tableId;

        const gridEl = document.createElement("div");
        gridEl.className = "mini-table-grid";

        const order = [7, 8, 9, 4, 5, 6, 1, 2, 3];

        order.forEach(boxId => {
            const boxEl = document.createElement("div");
            boxEl.className = "mini-box";

            if (table.isActive) {
                boxEl.classList.add("mini-box-online");
            }

            const isWinner =
                winnerTableId === table.tableId &&
                winnerBoxId === boxId;

            if (isWinner) {
                boxEl.classList.add("mini-box-winner");

                if (winnerJackpotId === "RUBY") boxEl.classList.add("mini-box-winner-ruby");
                if (winnerJackpotId === "GOLD") boxEl.classList.add("mini-box-winner-gold");
                if (winnerJackpotId === "JADE") boxEl.classList.add("mini-box-winner-jade");
            } else if ((table.activeBoxes || []).includes(boxId)) {
                boxEl.classList.add("mini-box-active");
            } else if ((table.recentBoxes || []).includes(boxId)) {
                boxEl.classList.add("mini-box-recent");
            }

            gridEl.appendChild(boxEl);
        });

        tableEl.appendChild(labelEl);
        tableEl.appendChild(gridEl);
        root.appendChild(tableEl);

        if (index < tables.length - 1) {
            const dots = document.createElement("div");
            dots.className = "mini-table-separator";
            dots.innerHTML = `<span></span><span></span><span></span>`;
            root.appendChild(dots);
        }
    });
}

async function refreshCurrentState() {
    const [health, snapshotResponse] = await Promise.all([
        fetchJson("/health"),
        fetchJson("/snapshot")
    ]);

    const snapshot = snapshotResponse.state ?? snapshotResponse;

    setServerStatus(
        true,
        `OK · stateVersion=${health.stateVersion} · lastEventId=${health.lastEventId}`
    );

    fillCards(snapshot);
    fillTablesState(snapshot);
    renderMiniTableStage(snapshot);

    const raw = document.getElementById("rawSnapshotBox");
    if (raw) raw.textContent = JSON.stringify(snapshotResponse, null, 2);
}

async function refreshBetBatches() {
    const params = new URLSearchParams();
    const tableId = document.getElementById("betBatchesTableId")?.value.trim() ?? "";
    const result = document.getElementById("betBatchesResult")?.value.trim() ?? "";

    params.set("limit", "50");
    if (tableId) params.set("tableId", tableId);
    if (result) params.set("result", result);

    const data = await fetchJson(`/admin/bet-batches?${params.toString()}`);
    if (window.jQuery && document.getElementById("betBatchesTable")) {
        window.jQuery("#betBatchesTable").bootstrapTable("load", data);
    }
}

async function refreshJackpotHits() {
    const params = new URLSearchParams();
    const tableId = document.getElementById("jackpotHitsTableId")?.value.trim() ?? "";
    const jackpotId = document.getElementById("jackpotHitsJackpotId")?.value.trim() ?? "";
    const status = document.getElementById("jackpotHitsStatus")?.value.trim() ?? "";

    params.set("limit", "50");
    if (tableId) params.set("tableId", tableId);
    if (jackpotId) params.set("jackpotId", jackpotId);
    if (status) params.set("status", status);

    const data = await fetchJson(`/admin/jackpot-hits?${params.toString()}`);
    if (window.jQuery && document.getElementById("jackpotHitsTable")) {
        window.jQuery("#jackpotHitsTable").bootstrapTable("load", data);
    }
}

async function refreshPendingWins() {
    const params = new URLSearchParams();
    const tableId = document.getElementById("pendingWinsTableId")?.value.trim() ?? "";
    const dealerConfirmed = document.getElementById("pendingWinsDealerConfirmed")?.value.trim() ?? "";

    if (tableId) params.set("tableId", tableId);
    if (dealerConfirmed) params.set("dealerConfirmed", dealerConfirmed);

    const query = params.toString();
    const data = await fetchJson(`/admin/pending-wins${query ? `?${query}` : ""}`);
    if (window.jQuery && document.getElementById("pendingWinsTable")) {
        window.jQuery("#pendingWinsTable").bootstrapTable("load", data);
    }
}

async function refreshJackpotSettings() {
    const data = await fetchJson("/admin/settings/jackpots");
    state.jackpotSettings = data;

    if (window.jQuery && document.getElementById("jackpotSettingsTable")) {
        window.jQuery("#jackpotSettingsTable").bootstrapTable("load", data);
    }
}

async function refreshServerSettings() {
    const data = await fetchJson("/admin/settings/server");
    state.serverSettings = data;

    const currencyEl = document.getElementById("serverCurrencyCode");
    const baseBetEl = document.getElementById("serverBaseBetAmount");

    if (currencyEl) currencyEl.value = data.currencyCode ?? "";
    if (baseBetEl) baseBetEl.value = data.baseBetAmount ?? 0;
}

async function refreshDashboard() {
    const dashboard = await fetchJson("/admin/dashboard");

    const latestHitBox = document.getElementById("dashboardLatestHitBox");
    if (latestHitBox) {
        if (dashboard.latestHit) {
            latestHitBox.innerHTML = `
                <div class="mb-2"><strong>Jackpot:</strong> ${escapeHtml(dashboard.latestHit.jackpotId)}</div>
                <div class="mb-2"><strong>Table:</strong> ${dashboard.latestHit.tableId}</div>
                <div class="mb-2"><strong>Trigger Box:</strong> ${dashboard.latestHit.triggerBoxId}</div>
                <div class="mb-2"><strong>Amount:</strong> ${formatMoney(dashboard.latestHit.winAmount, dashboard.currencyCode ?? "")}</div>
                <div class="mb-2"><strong>Status:</strong> ${escapeHtml(dashboard.latestHit.status)}</div>
                <div><strong>Hit At:</strong> ${tsFormatter(dashboard.latestHit.hitAt)}</div>
            `;
        } else {
            latestHitBox.innerHTML = `<div class="text-muted">No hits yet</div>`;
        }
    }

    const betBatchesEl = document.getElementById("todayStatBetBatches");
    const rubyHitsEl = document.getElementById("todayStatRubyHits");
    const goldHitsEl = document.getElementById("todayStatGoldHits");
    const jadeHitsEl = document.getElementById("todayStatJadeHits");

    if (betBatchesEl) betBatchesEl.textContent = dashboard.totalBatchesToday ?? "-";
    if (rubyHitsEl) rubyHitsEl.textContent = dashboard.rubyHitsToday ?? "-";
    if (goldHitsEl) goldHitsEl.textContent = dashboard.goldHitsToday ?? "-";
    if (jadeHitsEl) jadeHitsEl.textContent = dashboard.jadeHitsToday ?? "-";

    const latestBatchesBody = document.getElementById("dashboardLatestBatchesBody");
    if (latestBatchesBody) {
        latestBatchesBody.innerHTML = (dashboard.latestBatches || []).map(batch => `
            <tr>
                <td class="mono">${batch.id}</td>
                <td>${batch.tableId}</td>
                <td>${tsFormatter(batch.confirmedAt)}</td>
                <td class="mono">${(batch.boxIds || []).join(", ") || "-"}</td>
                <td>${escapeHtml(batch.result ?? "")}</td>
                <td>${escapeHtml(batch.winningJackpotId ?? "-")}</td>
                <td>${batch.winningBoxId ?? "-"}</td>
            </tr>
        `).join("");
    }

    const latestBatchesCount = document.getElementById("dashboardLatestBatchesCount");
    if (latestBatchesCount) {
        latestBatchesCount.textContent = (dashboard.latestBatches || []).length;
    }

    const turnoverInToday = document.getElementById("turnoverInToday");
    const turnoverOutToday = document.getElementById("turnoverOutToday");
    const turnoverInAllTime = document.getElementById("turnoverInAllTime");
    const turnoverOutAllTime = document.getElementById("turnoverOutAllTime");

    if (turnoverInToday) turnoverInToday.textContent = formatMoney(dashboard.totalInToday, dashboard.currencyCode ?? "");
    if (turnoverOutToday) turnoverOutToday.textContent = formatMoney(dashboard.totalOutToday, dashboard.currencyCode ?? "");
    if (turnoverInAllTime) turnoverInAllTime.textContent = formatMoney(dashboard.totalInAllTime, dashboard.currencyCode ?? "");
    if (turnoverOutAllTime) turnoverOutAllTime.textContent = formatMoney(dashboard.totalOutAllTime, dashboard.currencyCode ?? "");
}

async function refreshDevices() {
    const summary = await fetchJson("/admin/devices");
    const devices = summary.devices || [];

    const displays = devices.filter(d => d.deviceType === "DISPLAY" && d.isOnline);
    const tables = devices.filter(d => d.deviceType === "TABLE" && d.isOnline);

    const displaysOnlineEl = document.getElementById("dashboardDisplaysOnline");
    const tablesOnlineEl = document.getElementById("dashboardTablesOnline");
    if (displaysOnlineEl) displaysOnlineEl.textContent = displays.length;
    if (tablesOnlineEl) tablesOnlineEl.textContent = tables.length;

    const warn = document.getElementById("dashboardDisplayWarning");
    if (warn) {
        if (displays.length === 0) warn.classList.remove("d-none");
        else warn.classList.add("d-none");
    }

    const healthBadge = document.getElementById("devicesHealthBadge");
    if (healthBadge) {
        const tablesOnline = summary.tablesOnline ?? tables.length;
        const displaysOnline = summary.displaysOnline ?? displays.length;

        healthBadge.textContent = `Displays: ${displaysOnline} Tables: ${tablesOnline}`;
        healthBadge.classList.remove("bg-success", "bg-warning", "bg-danger", "bg-secondary");

        if (displaysOnline === 0 || tablesOnline === 0) {
            healthBadge.classList.add("bg-danger");
        } else if (tablesOnline < 8) {
            healthBadge.classList.add("bg-warning");
        } else {
            healthBadge.classList.add("bg-success");
        }
    }

    if (window.jQuery && document.getElementById("devicesTable")) {
        window.jQuery("#devicesTable").bootstrapTable("load", devices);
    }
}

function resetSettingsMessages() {
    const errorEl = document.getElementById("settingsSaveError");
    const successEl = document.getElementById("settingsSaveSuccess");

    if (errorEl) {
        errorEl.classList.add("d-none");
        errorEl.textContent = "";
    }
    if (successEl) {
        successEl.classList.add("d-none");
    }
}

function openEditJackpotSettingsModal(row) {
    resetSettingsMessages();

    const ids = {
        hidden: document.getElementById("settingsJackpotId"),
        readonly: document.getElementById("settingsJackpotIdReadonly"),
        resetAmount: document.getElementById("settingsResetAmount"),
        contributionPerBet: document.getElementById("settingsContributionPerBet"),
        hitFrequencyGames: document.getElementById("settingsHitFrequencyGames"),
        priorityOrder: document.getElementById("settingsPriorityOrder"),
        enabled: document.getElementById("settingsEnabled")
    };

    if (ids.hidden) ids.hidden.value = row.jackpotId;
    if (ids.readonly) ids.readonly.value = row.jackpotId;
    if (ids.resetAmount) ids.resetAmount.value = row.resetAmount;
    if (ids.contributionPerBet) ids.contributionPerBet.value = row.contributionPerBet;
    if (ids.hitFrequencyGames) ids.hitFrequencyGames.value = row.hitFrequencyGames;
    if (ids.priorityOrder) ids.priorityOrder.value = row.priorityOrder;
    if (ids.enabled) ids.enabled.checked = !!row.enabled;

    state.editJackpotModal?.show();
}

async function saveJackpotSettings(event) {
    event.preventDefault();
    resetSettingsMessages();

    const jackpotId = document.getElementById("settingsJackpotId")?.value;
    const payload = {
        resetAmount: Number(document.getElementById("settingsResetAmount")?.value),
        contributionPerBet: Number(document.getElementById("settingsContributionPerBet")?.value),
        hitFrequencyGames: Number(document.getElementById("settingsHitFrequencyGames")?.value),
        priorityOrder: Number(document.getElementById("settingsPriorityOrder")?.value),
        enabled: !!document.getElementById("settingsEnabled")?.checked
    };

    const saveBtn = document.getElementById("saveJackpotSettingsBtn");
    if (saveBtn) saveBtn.disabled = true;

    try {
        const response = await fetch(`/admin/settings/jackpots/${encodeURIComponent(jackpotId)}`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                Accept: "application/json"
            },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || `HTTP ${response.status}`);
        }

        document.getElementById("settingsSaveSuccess")?.classList.remove("d-none");

        await refreshJackpotSettings();
        await refreshCurrentState();

        setTimeout(() => state.editJackpotModal?.hide(), 400);
    } catch (e) {
        const errorEl = document.getElementById("settingsSaveError");
        if (errorEl) {
            errorEl.textContent = e.message || "Failed to save settings";
            errorEl.classList.remove("d-none");
        }
    } finally {
        if (saveBtn) saveBtn.disabled = false;
    }
}

function resetServerSettingsMessages() {
    const errorEl = document.getElementById("serverSettingsSaveError");
    const successEl = document.getElementById("serverSettingsSaveSuccess");

    if (errorEl) {
        errorEl.classList.add("d-none");
        errorEl.textContent = "";
    }
    if (successEl) {
        successEl.classList.add("d-none");
    }
}

async function saveServerSettings(event) {
    event.preventDefault();
    resetServerSettingsMessages();

    const payload = {
        currencyCode: document.getElementById("serverCurrencyCode")?.value.trim().toUpperCase(),
        baseBetAmount: Number(document.getElementById("serverBaseBetAmount")?.value)
    };

    const saveBtn = document.getElementById("saveServerSettingsBtn");
    if (saveBtn) saveBtn.disabled = true;

    try {
        const response = await fetch("/admin/settings/server", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                Accept: "application/json"
            },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || `HTTP ${response.status}`);
        }

        document.getElementById("serverSettingsSaveSuccess")?.classList.remove("d-none");

        await refreshServerSettings();
        await refreshCurrentState();
        await refreshDashboard();
    } catch (e) {
        const errorEl = document.getElementById("serverSettingsSaveError");
        if (errorEl) {
            errorEl.textContent = e.message || "Failed to save server settings";
            errorEl.classList.remove("d-none");
        }
    } finally {
        if (saveBtn) saveBtn.disabled = false;
    }
}

function appendLiveEventRow(msg) {
    liveEvents.unshift(msg);
    if (liveEvents.length > MAX_LIVE_EVENTS) {
        liveEvents.length = MAX_LIVE_EVENTS;
    }

    const tbody = document.getElementById("liveEventsBody");
    if (!tbody) return;

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
        setServerStatus(true, "WS connected");
    };

    ws.onmessage = async (event) => {
        try {
            const msg = JSON.parse(event.data);

            if (msg.type === "connected" || msg.type === "pong") {
                appendLiveEventRow(msg);
                return;
            }

            if (msg.type === "event") {
                appendLiveEventRow(msg);
                await refreshAll();
            }
        } catch (e) {
            console.error("Failed to parse WS message", e);
        }
    };

    ws.onclose = () => {
        setServerStatus(false, "WS disconnected, reconnecting...");
        setTimeout(connectWs, 2000);
    };

    ws.onerror = (e) => {
        console.error("WS error", e);
    };
}

function initTables() {
    if (!window.jQuery) return;

    if (document.getElementById("betBatchesTable")) {
        window.jQuery("#betBatchesTable").bootstrapTable({ data: [] });
    }
    if (document.getElementById("jackpotHitsTable")) {
        window.jQuery("#jackpotHitsTable").bootstrapTable({ data: [] });
    }
    if (document.getElementById("pendingWinsTable")) {
        window.jQuery("#pendingWinsTable").bootstrapTable({ data: [] });
    }
    if (document.getElementById("jackpotSettingsTable")) {
        window.jQuery("#jackpotSettingsTable").bootstrapTable({ data: [] });
    }
    if (document.getElementById("devicesTable")) {
        window.jQuery("#devicesTable").bootstrapTable({ data: [] });
    }
}

function setupFilters() {
    document.getElementById("betBatchesFilterForm")?.addEventListener("submit", async (e) => {
        e.preventDefault();
        await refreshBetBatches();
    });

    document.getElementById("jackpotHitsFilterForm")?.addEventListener("submit", async (e) => {
        e.preventDefault();
        await refreshJackpotHits();
    });

    document.getElementById("pendingWinsFilterForm")?.addEventListener("submit", async (e) => {
        e.preventDefault();
        await refreshPendingWins();
    });
}

function setupSettingsUi() {
    const modalElement = document.getElementById("editJackpotSettingsModal");
    if (modalElement && window.bootstrap) {
        state.editJackpotModal = new bootstrap.Modal(modalElement);
    }

    document.getElementById("editJackpotSettingsForm")
        ?.addEventListener("submit", saveJackpotSettings);

    document.getElementById("refreshSettingsBtn")
        ?.addEventListener("click", refreshJackpotSettings);

    document.getElementById("serverSettingsForm")
        ?.addEventListener("submit", saveServerSettings);

    document.getElementById("refreshServerSettingsBtn")
        ?.addEventListener("click", refreshServerSettings);
}

function setupToolbar() {
    document.getElementById("refreshDashboardBtn")
        ?.addEventListener("click", refreshDashboard);

    document.getElementById("refreshDevicesBtn")
        ?.addEventListener("click", refreshDevices);

    document.getElementById("refreshAllBtn")
        ?.addEventListener("click", refreshAll);

    const autoBtn = document.getElementById("autoRefreshBtn");
    if (autoBtn) {
        autoBtn.addEventListener("click", () => {
            state.autoRefresh = !state.autoRefresh;
            autoBtn.textContent = `Auto: ${state.autoRefresh ? "ON" : "OFF"}`;

            if (state.autoRefresh) startAutoRefresh();
            else stopAutoRefresh();
        });
    }

    document.getElementById("clearLiveEventsBtn")
        ?.addEventListener("click", () => {
            liveEvents.length = 0;
            const tbody = document.getElementById("liveEventsBody");
            if (tbody) tbody.innerHTML = "";
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

async function refreshAll() {
    try {
        await Promise.all([
            refreshDashboard(),
            refreshCurrentState(),
            refreshBetBatches(),
            refreshJackpotHits(),
            refreshPendingWins(),
            refreshJackpotSettings(),
            refreshServerSettings(),
            refreshDevices()
        ]);
    } catch (e) {
        console.error(e);
        setServerStatus(false, `ERROR · ${e.message}`);
    }
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