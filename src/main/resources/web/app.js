const $ = (id) => document.getElementById(id);

function tablePlayers(data) {
  const raw = data.players && data.players.length
    ? data.players
    : [data.you, data.opponent].filter(Boolean);
  const seen = new Set();
  const list = [];
  for (const p of raw) {
    if (!p || seen.has(p.id)) continue;
    seen.add(p.id);
    list.push(p);
  }
  return list;
}

function teamColor(p) {
  return p && p.color ? p.color : "green";
}

function displayName(p, fallback) {
  if (p && p.human) return "You";
  if (p && p.corporation && p.corporation !== "Unknown") return p.corporation;
  return (p && p.name) || fallback || "Player";
}

function boardTitle(p) {
  if (p && p.human) return "You";
  if (p && p.corporation && p.corporation !== "Unknown") return p.corporation;
  return (p && p.name) || "Player";
}

function boardSubtitle(p) {
  if (!p) return "";
  const bits = [];
  if (p.human && p.corporation && p.corporation !== "Unknown") bits.push(p.corporation);
  if (!p.human && p.name && p.name !== boardTitle(p)) bits.push(p.name);
  if (p.human) bits.push("You");
  return bits.join(" · ");
}

function renderCubes(p) {
  const rows = [
    ["M€", p.megaCredits, p.megaCreditProd],
    ["Steel", p.steel, p.steelProd],
    ["Titanium", p.titanium, p.titaniumProd],
    ["Plant", p.plants, p.plantProd],
    ["Energy", p.energy, p.energyProd],
    ["Heat", p.heat, p.heatProd],
  ];
  return `<table class="cubes">
    <tr><th>Resource</th><th class="num">Qty</th><th class="num">Prod</th></tr>
    ${rows.map(([n, q, pr]) => `<tr><td>${n}</td><td class="num">${q ?? 0}</td><td class="num prod">${pr ?? 0}</td></tr>`).join("")}
  </table>
  <p class="awards">TR ${p.tr ?? 20} · Cities on Mars ${p.citiesOnMars ?? 0} · Greeneries ${p.greeneries ?? 0} · Oceans ${p.oceans ?? 0}</p>`;
}

function renderTags(tags) {
  if (!tags) return "";
  return `<div class="tags">${Object.entries(tags).map(([k, v]) =>
    `<span class="tag ${v ? "" : "zero"}">${k} ${v}</span>`
  ).join("")}</div>`;
}

function extra(text) {
  return (text || "").replace(/\n/g, " · ").replace(/\s+/g, " ").trim();
}

function tokenChip(c) {
  if (!c.tokenType && !(c.tokens > 0)) return "";
  const kind = c.tokenType || "token";
  const n = c.tokens ?? 0;
  const label = n === 1 ? kind : kind + "s";
  return `<span class="token token-${kind}${n ? "" : " zero"}" title="${n} ${label} on this card">${n}</span>`;
}

function renderCards(title, cards, cls) {
  if (!cards || !cards.length) return "";
  return `<h3 class="section-title">${title}</h3>
    <div class="cards">${cards.map((c) => `
      <div class="card ${cls}">
        <div class="card-head">
          <h3>${c.name}</h3>
          ${tokenChip(c)}
        </div>
        <p>${(c.tags || []).join(", ")}${c.extra ? " — " + extra(c.extra) : ""}</p>
      </div>`).join("")}</div>`;
}

function renderPlayer(p) {
  const awards = [...(p.milestones || []), ...(p.awards || [])];
  const color = teamColor(p);
  const you = !!p.human;
  return `<article class="board color-${color}${you ? " you" : ""}">
    <h2><span class="swatch" aria-hidden="true"></span>${boardTitle(p)}</h2>
    <p class="corp">${boardSubtitle(p)}</p>
    ${renderCubes(p)}
    ${renderTags(p.tags)}
    ${awards.length ? `<p class="awards">${awards.join(" · ")}</p>` : ""}
    ${renderCards("Blue cards", p.blueCards, "blue")}
    ${renderCards("Automated", p.greenCards, "green")}
    ${renderCards("Events", p.events, "red")}
  </article>`;
}

function render(data) {
  $("meta").textContent = `Gen ${data.generation ?? "?"} · ${data.phase || ""} · ${data.board || ""} · ${data.gameId ? "Game " + data.gameId : "no game yet"}`;
  $("live").textContent = data.live ? "live" : "idle";
  $("live").classList.toggle("on", !!data.live);
  if (data.url) {
    $("qr-link").href = data.url;
    $("qr-link").title = data.url;
    $("qr-url").textContent = data.url.replace(/^https?:\/\//, "").replace(/\/$/, "");
  }
  const hint = $("phone-hint");
  const phone = (data.url || "").replace(/\/$/, "");
  hint.hidden = false;
  if (data.firewallOpen === false) {
    hint.innerHTML = `Windows Firewall is blocking phones. Approve the Windows prompt when TM Companion starts, then open <code>${phone}</code>.`;
  } else {
    hint.innerHTML = `On your phone, same Wi-Fi as usual. Open <code>${phone}</code>. Chrome may warn about a local certificate — tap Advanced, then proceed.`;
  }

  const play = data.activePlay;
  const banner = $("banner");
  banner.classList.remove("yours", "color-green", "color-yellow", "color-red", "color-blue", "color-black");
  if (play && play.cardName) {
    const playColor = play.playerColor || (play.yours ? teamColor(data.you) : teamColor((data.opponents || [])[0]));
    banner.classList.add("color-" + (playColor || "green"));
    if (play.yours) banner.classList.add("yours");
    $("banner-kicker").textContent = `${play.yours ? "You" : play.playerLabel} · ${play.colorLabel || "in play"}${play.placing ? " · placing " + play.placing : ""}`;
    $("banner-title").textContent = play.cardName;
    $("banner-effect").textContent = play.effect || "";
    $("banner-tips").innerHTML = (play.remember || []).map((t) => `<li>${t}</li>`).join("");
  } else {
    $("banner-kicker").textContent = "No card in flight";
    $("banner-title").textContent = "Glance here instead of opening the other tableaus.";
    $("banner-effect").textContent = "When a card needs a tile, the effect stays pinned so the placement UI cannot hide it.";
    $("banner-tips").innerHTML = "";
  }

  const players = tablePlayers(data);
  const boards = $("boards");
  boards.className = "boards players-" + Math.max(1, Math.min(5, players.length));
  boards.innerHTML = players.map(renderPlayer).join("");
  renderScore(data, players);
}

function scoreOf(data, p) {
  const byId = (data.score && data.score.byId) || {};
  return byId[p.id] || byId[String(p.id)] || (p.human ? data.score?.you : {}) || {};
}

function renderScore(data, players) {
  const box = $("scoreboard");
  const score = data.score;
  if (!score || !players.length) {
    box.hidden = true;
    return;
  }
  box.hidden = false;
  const breakdowns = players
    .map((p) => ({ p, b: scoreOf(data, p) }))
    .sort((a, c) => (c.b.total ?? 0) - (a.b.total ?? 0) || a.p.id - c.p.id);
  const best = Math.max(...breakdowns.map(({ b }) => b.total ?? 0));
  $("score-line").innerHTML = breakdowns.map(({ p, b }, i) => {
    const name = displayName(p, "P" + p.id);
    const ahead = (b.total ?? 0) === best && best > 0;
    const sep = i === 0 ? "" : `<span class="score-vs">—</span>`;
    return `${sep}<span class="score-chip color-${teamColor(p)}${ahead ? " ahead" : ""}"><span class="name">${name}</span> <strong>${b.total ?? 0}</strong></span>`;
  }).join("") + `<span class="score-chevron" aria-hidden="true">▾</span>`;

  $("score-head").innerHTML = `<tr><th></th>${breakdowns.map(({ p }) =>
    `<th class="team color-${teamColor(p)}">${displayName(p, "P" + p.id)}</th>`
  ).join("")}</tr>`;

  const rows = [
    ["TR", (b) => b.tr, false],
    ["Milestones", (b) => b.milestones, false],
    ["Awards", (b) => b.awards, false],
    ["Greeneries", (b) => b.greeneries, false],
    ["Cities", (b) => b.cities, false],
    ["Cards + tokens", (b) => b.cards, true],
  ];
  $("score-lines").innerHTML = rows.map(([n, pick, drill]) =>
    `<tr${drill ? ` class="clickable${cardsOpen ? " open" : ""}" data-drill="cards" title="Click for card VP"` : ""}><td>${n}</td>${
      breakdowns.map(({ b }) => `<td>${pick(b) ?? 0}</td>`).join("")
    }</tr>`
  ).join("");

  $("score-cards").innerHTML = breakdowns.map(({ p, b }) => `
    <div class="color-${teamColor(p)}">
      <h3 class="section-title">${displayName(p, "P" + p.id)} cards</h3>
      <ul>${(b.cardDetails || []).map((d) => `<li>${d}</li>`).join("") || "<li>None</li>"}</ul>
    </div>`).join("");
  $("score-note").textContent = score.note || "";
  $("score-note").hidden = !score.note;
  $("score-details").hidden = !scoreOpen;
  $("score-cards").hidden = !cardsOpen;
  $("score-toggle").setAttribute("aria-expanded", scoreOpen ? "true" : "false");
}

let scoreOpen = false;
let cardsOpen = false;
let scoreUiBound = false;

function bindScoreUi() {
  if (scoreUiBound) return;
  scoreUiBound = true;
  $("score-toggle").addEventListener("click", () => {
    scoreOpen = !scoreOpen;
    if (!scoreOpen) cardsOpen = false;
    $("score-details").hidden = !scoreOpen;
    $("score-cards").hidden = !cardsOpen;
    $("score-toggle").setAttribute("aria-expanded", scoreOpen ? "true" : "false");
  });
  $("score-lines").addEventListener("click", (ev) => {
    const row = ev.target.closest("tr[data-drill='cards']");
    if (!row) return;
    cardsOpen = !cardsOpen;
    $("score-cards").hidden = !cardsOpen;
    row.classList.toggle("open", cardsOpen);
  });
}

async function tick() {
  try {
    const res = await fetch("/api/state", { cache: "no-store" });
    render(await res.json());
  } catch (err) {
    $("meta").textContent = "Companion lost the local server — is the Java process still running?";
  }
}

tick();
setInterval(tick, 500);
bindScoreUi();
