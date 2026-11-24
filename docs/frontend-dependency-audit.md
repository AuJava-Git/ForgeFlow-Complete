# 前端依赖审计记录

## 处理结果

初始 `npm audit` 报告 14 项漏洞：8 moderate、4 high、2 critical。按审计建议升级了有兼容补丁的直接依赖：Axios `1.7.9 → 1.21.1`、Element Plus `2.9.3 → 2.14.8`、Vue `3.5.13 → 3.5.46`、Vite `6.0.7 → 6.4.6`，并将 Vitest `2.1.8 → 5.1.1` 以修复其旧版测试运行器及关联 worker/开发服务器依赖中的高危与严重问题。版本信息已锁定在 `frontend/package.json` 与 `frontend/package-lock.json`。

升级后，前端测试曾为 12/12 通过，`npm run build` 成功；本次制造闭环页面扩展后，最新全量测试为 **13/13 通过**，生产构建成功。最新执行的 `npm audit --omit=dev` 报告生产依赖 0 项漏洞。全量 `npm audit` 仍报告 **4 项 moderate**，均在开发/测试依赖链 `@vue/test-utils@2.4.6 → js-beautify@1.15.4 → editorconfig / glob / minimatch`，不属于生产打包依赖。`npm audit fix` 没有进一步变更；要清除剩余项需要通过 override 或跨出这些传递依赖声明的版本范围升级。为避免对格式化/测试工具强制替换主版本造成未验证的兼容问题，本次保留并记录该残余风险，不称审计完全清零。

Vitest 5 要求 Vite `>=6.4.0`、Node.js `>=22.12.0`；项目使用 Vite `6.4.6`，`frontend/package.json` 也声明了 Node.js 最低版本。迁移前检查了官方迁移说明，现有 12 个测试通过。

## 相关公告与迁移说明

- [Axios GHSA-3p68-rc4w-qgx5](https://github.com/advisories/GHSA-3p68-rc4w-qgx5)
- [Vue server-renderer GHSA-g2v6-rqmx-r4w6](https://github.com/advisories/GHSA-g2v6-rqmx-r4w6)
- [Vite GHSA-p9ff-h696-f583](https://github.com/advisories/GHSA-p9ff-h696-f583)
- [Vitest GHSA-9crc-q9x8-hgqq](https://github.com/vitest-dev/vitest/security/advisories/GHSA-9crc-q9x8-hgqq)
- [Glob GHSA-3g3m-2fjr-6pw4](https://github.com/advisories/GHSA-3g3m-2fjr-6pw4)
- [Minimatch GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249)
- [Vitest 官方迁移指南](https://vitest.dev/guide/migration/)
