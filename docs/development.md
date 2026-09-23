# 开发规范

## Java

- JDK 21。
- 注释使用中文标准 Javadoc。
- 运行日志使用英文 SLF4J 参数化消息。
- Core 禁止依赖 Spring、Vert.x、Jetcd、Fory、CGLIB。
- Adapter 不得向 Core API 泄漏第三方类型。
- 单行不超过 120 字符，不使用 wildcard import、Tab、行尾空格。

## 构建

```bash
python3 scripts/check_project.py
mvn -B -ntp clean verify -Pquality
```

## 修改热路径

必须说明对象分配、锁/CAS、Map/Queue、线程切换、序列化拷贝、系统调用和尾延迟变化，并提供可重复 benchmark。
