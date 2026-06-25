#!/usr/bin/env node
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const LARK = process.env.LARK_CLI || 'C:\\Users\\x21107\\AppData\\Roaming\\npm\\lark-cli.cmd';
const DOC = 'https://q00enigbkuh.feishu.cn/wiki/O7YPwqYNoi2icrk3X7ccLSJCnae'; // sync: tools/feishu_doc_config.py
const ROOT = path.join(__dirname, '_feishu_blocks');

const JOBS = [
  ['doxcn4VvZlCLiEmimKCzz2Z7UDb', 'intro.xml'],
  ['doxcnhoZM3EEDAx5HDlB06bymJe', 'h1_conclusion.xml'],
  ['doxcn0YQjvuk4xDqZ1CeytFLG1e', 'h2_r1.xml'],
  ['doxcnve1TJ4aoc1ajt2ctSwMynh', 'r1_conclusion_table.xml'],
  ['doxcnoWO00tKrhRD2zKkMpSaIEe', 'h2_r2.xml'],
  ['doxcn6qvcyt74gcBoZfJ6P22Hdh', 'r2_conclusion_table.xml'],
  ['doxcn0PgDVZ7nAe74wwM79xIKuc', 'h3_r1_bj.xml'],
  ['doxcnhBkC3i72mOuxGmT3KAZjtg', 'R1-bj.xml'],
  ['doxcndEEWnbFZz3A0EiFNqdzMXy', 'h3_r1_gz.xml'],
  ['doxcnNvC2dTyZZR7oC9EoNX0g88', 'R1-gz.xml'],
  ['doxcnf5ySO2NQE3etDxAtnbGfOg', 'h3_r1_sg.xml'],
  ['doxcnKaMizACcNDXL51uPhkfIqg', 'R1-sg.xml'],
  ['doxcnStqWdRvIVje3DsFv171rjd', 'h3_r2_single_gz.xml'],
  ['doxcn9H12afYSdXc6j5jtuWhbHd', 'R2-single.xml'],
  ['doxcnoRRQSPGEYfIWcn8d5zwxMd', 'h3_r2_dual_gz.xml'],
  ['doxcnn184rxBQ4cn943qehSSjqh', 'R2-dual.xml'],
  ['doxcnAuZmkrlseD7Bf2e5zQLcog', 'h3_r2_dual_xian.xml'],
  ['doxcn5siwf37rnc7xxEgOtu3wPb', 'R2-dual-xian.xml'],
  ['doxcnlzASgkz8nhtqjgT1soB8re', 'h3_r2_single_xian.xml'],
  ['doxcnHcFwE2FqSZWoBT63STfeNb', 'R2-single-xian.xml'],
];

for (const [blockId, file] of JOBS) {
  const content = fs.readFileSync(path.join(ROOT, file), 'utf8');
  const out = execSync(
    `"${LARK}" docs +update --api-version v2 --doc "${DOC}" --command block_replace --block-id ${blockId} --content ${JSON.stringify(content)}`,
    { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024, shell: true },
  );
  const data = JSON.parse(out);
  const result = data?.data?.result;
  const rev = data?.data?.document?.revision_id;
  console.log(`${file}: ${result} rev=${rev}`);
}

// callout insert (ignore duplicate)
try {
  const callout = fs.readFileSync(path.join(ROOT, 'callout.xml'), 'utf8');
  const out = execSync(
    `"${LARK}" docs +update --api-version v2 --doc "${DOC}" --command block_insert_after --block-id doxcn4VvZlCLiEmimKCzz2Z7UDb --content ${JSON.stringify(callout)}`,
    { encoding: 'utf8', shell: true },
  );
  console.log('callout:', JSON.parse(out)?.data?.result);
} catch (e) {
  console.log('callout: skipped');
}
