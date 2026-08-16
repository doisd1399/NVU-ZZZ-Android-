import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { readFileSync } from 'fs';

async function run() {
  const env = await initializeTestEnvironment({
    projectId: 'demo-test',
    firestore: { host: '127.0.0.1', port: 8080, rules: readFileSync('firestore.rules.v6', 'utf8') }
  });
  console.log("Success!");
  await env.cleanup();
}
run().catch(console.error);
