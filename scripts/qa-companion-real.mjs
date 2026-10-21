import { mkdir, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const options = parseArgs(process.argv.slice(2));
const spotCoreRoot = resolve(options['spotcore-root'] || '');
const fixtureDirectory = resolve(options['fixture-directory'] || '');
const outputDirectory = resolve(options['output-directory'] || '');
const readyPath = resolve(options['ready-path'] || join(outputDirectory, '..', 'companion-ready.json'));
const port = Number(options.port || 44182);
const controlPort = Number(options['control-port'] || 44183);
if (!spotCoreRoot || !fixtureDirectory || !outputDirectory || !Number.isInteger(port)) {
  throw new Error('Usage: node qa-companion-real.mjs --spotcore-root <path> --fixture-directory <path> --output-directory <path> --port <port>');
}

const importFromSpotCore = (relativePath) => import(pathToFileURL(join(spotCoreRoot, relativePath)).href);
const [{ default: DatabaseManager }, { default: LibraryManager }, { createCompanionServer }] = await Promise.all([
  importFromSpotCore('src/library/DatabaseManager.js'),
  importFromSpotCore('src/library/LibraryManager.js'),
  importFromSpotCore('src/companion/CompanionServer.js')
]);

await mkdir(outputDirectory, { recursive: true });
const databasePath = join(outputDirectory, 'spotcore-qa-library.sqlite');
const settings = {
  companion: { enabled: true, host: '127.0.0.1', port, token: '', devices: [] }
};
const settingsStore = {
  async getSettings() { return structuredClone(settings); },
  async updateSettings(partial = {}) {
    settings.companion = { ...settings.companion, ...(partial.companion || {}) };
    return structuredClone(settings);
  }
};

const databaseManager = new DatabaseManager(databasePath);
await databaseManager.initialize();
const libraryManager = new LibraryManager();
const fixtureFiles = ['tone-440.wav', 'tone-880.mp3', 'silence.wav'];
const titles = ['QA Track A', 'QA Track B', 'QA Track C'];
const scanned = [];
for (let index = 0; index < fixtureFiles.length; index += 1) {
  const source = await libraryManager.scanFile(join(fixtureDirectory, fixtureFiles[index]), { includeCoverArt: false, includeLocalCoverArt: false });
  scanned.push({
    ...source,
    id: `spotamp-real-qa-${index + 1}`,
    title: titles[index],
    artist: 'SpotAmp Real QA',
    album: 'SpotCore Companion Fixtures',
    albumArtist: 'SpotAmp Real QA',
    genre: 'QA',
    coverArt: null
  });
}
databaseManager.addSongs(scanned, { directoryPath: fixtureDirectory, returnLibrary: false, updateFolder: true });

const companion = createCompanionServer({ databaseManager, settingsStore, port });
const sockets = new Set();
const recoveryEvents = [];
let companionRunning = false;
companion.server.on('connection', (socket) => {
  sockets.add(socket);
  socket.once('close', () => sockets.delete(socket));
});
const requestLog = [];
companion.server.on('request', (request, response) => {
  const entry = {
    method: request.method,
    path: String(request.url || '/').split('?')[0],
    range: request.headers.range ? 'present' : 'absent',
    authorizationPresent: Boolean(request.headers.authorization)
  };
  response.once('finish', () => {
    requestLog.push({ ...entry, status: response.statusCode });
    writeEvidence().catch(() => {});
  });
});

const pairing = await companion.beginPairing('SpotAmp real QA device');
await companion.start();
companionRunning = true;
const controlServer = createServer(async (request, response) => {
  if (request.method !== 'POST' || !['/pause', '/resume'].includes(request.url)) {
    response.writeHead(404).end();
    return;
  }
  try {
    if (request.url === '/pause' && companionRunning) {
      for (const socket of sockets) socket.destroy();
      await companion.stop();
      companionRunning = false;
      recoveryEvents.push({ action: 'pause', at: new Date().toISOString() });
    } else if (request.url === '/resume' && !companionRunning) {
      await companion.start();
      companionRunning = true;
      recoveryEvents.push({ action: 'resume', at: new Date().toISOString() });
    }
    await writeEvidence();
    response.writeHead(200, { 'content-type': 'application/json' }).end(JSON.stringify({ running: companionRunning }));
  } catch (error) {
    response.writeHead(500, { 'content-type': 'application/json' }).end(JSON.stringify({ error: String(error) }));
  }
});
await new Promise((resolve, reject) => {
  controlServer.once('error', reject);
  controlServer.listen(controlPort, '127.0.0.1', resolve);
});
const ready = {
  contractVersion: '1.4',
  baseUrl: pairing.baseUrl,
  pairingId: pairing.pairingId,
  pairingPayloadEncoded: encodeURIComponent(pairing.payload),
  port,
  controlBaseUrl: `http://127.0.0.1:${controlPort}`,
  spotCoreImplementation: 'adjacent SpotCore source CompanionServer.js',
  fixtureTitles: titles,
  generatedAtUtc: new Date().toISOString()
};
await writeFile(readyPath, JSON.stringify(ready, null, 2));
await writeEvidence();
console.log(`READY ${JSON.stringify({ baseUrl: ready.baseUrl, port, controlBaseUrl: ready.controlBaseUrl, fixtureTitles: titles })}`);

async function writeEvidence() {
  await writeFile(join(outputDirectory, 'server-evidence.json'), JSON.stringify({
    implementation: 'real SpotCore CompanionServer',
    contractVersion: '1.4',
    fixtureTitles: titles,
    requests: requestLog,
    recoveryEvents
  }, null, 2));
}

async function shutdown() {
  if (companionRunning) await companion.stop().catch(() => {});
  await new Promise((resolve) => controlServer.close(resolve));
  databaseManager.close();
  await writeEvidence();
  process.exit(0);
}
process.once('SIGINT', shutdown);
process.once('SIGTERM', shutdown);
process.stdin.resume();

function parseArgs(values) {
  const result = {};
  for (let index = 0; index < values.length; index += 1) {
    const value = values[index];
    if (!value.startsWith('--')) continue;
    result[value.slice(2)] = values[index + 1] || '';
    index += 1;
  }
  return result;
}
