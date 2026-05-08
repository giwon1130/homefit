// pnpm 모노레포에서 Metro 가 root node_modules + apps/mobile 의 호이스트된 모듈을
// 둘 다 탐색하도록 설정. .npmrc 의 node-linker=hoisted 와 함께 작동.
//
// (Expo SDK 54 의 metro.config.js 기본값 + watchFolders 추가)

const { getDefaultConfig } = require("expo/metro-config");
const path = require("path");

const projectRoot = __dirname;
const workspaceRoot = path.resolve(projectRoot, "../..");

const config = getDefaultConfig(projectRoot);

// 1. workspace 루트도 watch — 호이스트된 모듈이 거기 있을 수 있음.
config.watchFolders = [workspaceRoot];

// 2. node_modules 탐색 순서: 프로젝트 → 워크스페이스 루트.
config.resolver.nodeModulesPaths = [
  path.resolve(projectRoot, "node_modules"),
  path.resolve(workspaceRoot, "node_modules"),
];

// 3. pnpm 의 .pnpm 가상 저장소 안 모듈은 symlink 라 disable.
config.resolver.disableHierarchicalLookup = true;

module.exports = config;
