const fs = require("fs");
const vm = require("vm");
const path = require("path");

let src = fs.readFileSync(path.join(__dirname, "..", "cards.src.js"), "utf8");
src = src.replace(/tags:\[([^\]]*)],\s*tags:\[([^\]]*)]/g, "tags:[$1,$2]");
src = src.replace(/^\/\/.*$/gm, "");
const sandbox = {};
vm.createContext(sandbox);
vm.runInContext(src + "\nthis.__cards = oAllCards;", sandbox);
const raw = sandbox.__cards;

function displayName(key) {
  return key
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/([A-Z]+)([A-Z][a-z])/g, "$1 $2")
    .replace("GHG", "GHG")
    .replace("EOS ", "Eos ")
    .replace("CEO s", "CEO's")
    .replace("CEOs", "CEO's")
    .replace("AI Central", "AI Central")
    .replace("UNMI ", "UNMI ");
}

const cards = Object.entries(raw).map(([key, c]) => {
  const tags = [...new Set(c.tags || [])];
  if (c.color === "red" && !tags.includes("event")) tags.push("event");
  const resources = c.resources || {};
  const prod = c.prod || {};
  const extra = (c.cardExtra || "").replace(/\r/g, "").trim();
  const place = [];
  if (resources.ocean) place.push(resources.ocean > 1 ? `${resources.ocean} oceans` : "ocean");
  if (resources.city) place.push("city");
  if (resources.greenery) place.push("greenery");
  const extraLower = extra.toLowerCase();
  if (/special tile/.test(extraLower) && !place.length) place.push("special tile");
  if (/place a city/.test(extraLower) && !place.includes("city")) place.push("city");
  if (/place a greenery/.test(extraLower) && !place.includes("greenery")) place.push("greenery");
  if (/place 1 ocean|place an ocean|ocean tile/.test(extraLower) && !place.includes("ocean") && !place.some((p) => p.includes("ocean"))) {
    place.push("ocean");
  }
  return {
    key,
    name: displayName(key),
    number: c.cardNum || "",
    type: c.type || "proj",
    color: c.color || "green",
    cost: c.cost ? Number(c.cost) : null,
    tags,
    vp: c.vp ?? null,
    resources,
    production: prod,
    extra,
    place,
    req: c.req || {},
  };
});

const outDir = path.join(__dirname, "..", "src", "main", "resources");
fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(path.join(outDir, "cards.json"), JSON.stringify(cards, null, 2));
console.log("Wrote", cards.length, "cards");
