## 变更说明

<!-- 说明问题、方案和影响。 -->

## 类型

- [ ] Bug fix
- [ ] Feature
- [ ] Performance
- [ ] Compatibility
- [ ] Security
- [ ] Documentation
- [ ] Release engineering

## 验证

- [ ] `python3 scripts/check_project.py`
- [ ] `mvn -B -ntp clean verify -Pquality`
- [ ] 涉及 Registry/Transport 时已运行对应集成/Chaos
- [ ] 涉及 Wire/Schema 时已运行 Rolling Compatibility
- [ ] 涉及热路径时提供 Benchmark/Evidence

## 兼容性

- [ ] 不改变 Wire v1
- [ ] 不改变 Stable Type ID 规则
- [ ] 不改变 Schema Fingerprint v1
- [ ] 不破坏 Public Core API
- [ ] 若存在不兼容变化，已明确新兼容边界和迁移方案

## 文档

- [ ] README 中英文同步
- [ ] 相关设计/配置/升级文档同步
- [ ] 没有引入 Secret 或环境特定凭据
