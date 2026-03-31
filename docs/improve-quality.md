# How to Improve Fast Formula Generation Quality

AI 生成质量取决于 ChromaDB 向量知识库中的参考材料。以下是按优先级排列的提升方法。

## 当前知识库状态

| 类型 | 数量 | 位置 |
|------|------|------|
| 公式样例 (.ff) | 5 | `backend/data/samples/` |
| 语法参考文档 | 1 | `backend/data/docs/ff_syntax_reference.txt` |
| 内置函数文档 | 1 | `backend/data/docs/ff_builtin_functions.txt` |
| Time & Labor 指南 | 1 | `backend/data/docs/ff_time_labor_guide.txt` |
| DBI 注册表 | 65 项 | `backend/data/dbi_registry/time_labor_dbis.json` |

---

## Priority 1: 直接提升生成质量

### 1.1 导入公司已有的 Fast Formula（最高价值）

把在 Oracle HCM 中实际使用的公式导出，每个公式一个 `.ff` 文件 + 一个 `.json` 元数据文件：

```
backend/data/samples/
├── overtime_weekly.ff
├── overtime_weekly.json      ← {"formula_type": "TIME_LABOR", "use_case": "overtime", "complexity": "medium"}
├── shift_night_premium.ff
├── shift_night_premium.json
├── ...
```

**效果：** AI 会模仿你们的命名风格、变量习惯和业务逻辑模式，生成的代码和现有代码保持一致。

**建议数量：** 20-30 个真实公式可以带来质的飞跃。

### 1.2 导入客户特定的 DBI 列表

从 Oracle HCM 环境导出 DBI 列表，或从 MOS Doc ID 1990057.1 下载 WFM Database Items 电子表格：

```
# 替换或补充
backend/data/dbi_registry/time_labor_dbis.json
```

格式：
```json
[
  {"name": "YOUR_CUSTOM_DBI", "data_type": "NUMBER", "module": "TIME_LABOR", "description": "说明"},
  ...
]
```

**效果：** AI 生成时会引用正确的 DBI 名称，编辑器自动补全也会包含这些 DBI。

### 1.3 导入业务规则文档

把 HR 部门的政策文档转成纯文本放到 `backend/data/docs/`：

```
backend/data/docs/
├── company_overtime_policy.txt      ← 加班政策（阈值、倍率、审批规则）
├── shift_rules.txt                  ← 排班规则（夜班/晚班补贴比例）
├── holiday_calendar_rules.txt       ← 假期规则（法定假日、公司假日倍率）
├── leave_accrual_rules.txt          ← 假期累计规则（按工龄、按级别）
└── pay_calculation_rules.txt        ← 薪资计算规则
```

**效果：** 用户说 "write overtime formula" 时，AI 不仅参考语法，还参考你们的具体业务规则。

---

## Priority 2: 提升语法准确性

### 2.1 补充更多公式场景

当前只有 5 个样例，建议补充以下场景：

| 场景 | 建议文件名 | 说明 |
|------|-----------|------|
| 周末加班 | `weekend_overtime.ff` | 周六1.5x / 周日2x |
| 多层加班 | `tiered_overtime.ff` | 1.5x / 2x / 3x 分段 |
| 按小时计薪 | `hourly_pay_calc.ff` | 基础小时工资计算 |
| 按天计薪 | `daily_pay_calc.ff` | 日薪计算 |
| 请假扣减 | `absence_deduction.ff` | 请假天数扣减工资 |
| 加班审批检查 | `overtime_approval_check.ff` | 验证加班是否经过审批 |
| 排班合规检查 | `schedule_compliance.ff` | 检查工时是否符合劳动法 |
| 跨天工时计算 | `cross_day_hours.ff` | 跨午夜的工时计算 |
| 输入验证 | `input_validation.ff` | WAS DEFAULTED 检查模式 |
| 循环计算 | `iterative_calc.ff` | WHILE LOOP 示例 |
| 查表计算 | `table_lookup.ff` | GET_TABLE_VALUE 使用示例 |
| 多值返回 | `multi_output.ff` | RETURN var1, var2, var3 |

### 2.2 导入 Oracle 官方示例

从 Oracle 文档或 MOS 中找到的标准示例：

```
backend/data/docs/
├── oracle_payroll_examples.txt       ← 官方 payroll 公式示例
├── oracle_absence_examples.txt       ← 官方 absence 公式示例
└── oracle_time_entry_rule_examples.txt ← 官方 time entry 公式示例
```

---

## Priority 3: 高级场景

### 3.1 错误修复配对

创建 "错误代码 → 修复后代码" 的配对文档，帮助 AI 更好地修复错误：

```
backend/data/docs/common_ff_errors.txt
```

示例内容：
```
WRONG: IF hours > 40
  overtime = hours - 40
FIX: IF hours > 40 THEN
  overtime = hours - 40
END IF
EXPLANATION: Missing THEN keyword and END IF

WRONG: INPUTS ARE hours
  pay = hours * rate
FIX: INPUTS ARE hours (NUMBER)
  DEFAULT FOR rate IS 0
  pay = hours * rate
EXPLANATION: rate not declared, INPUTS missing type annotation
```

### 3.2 行业特定模板

如果你们服务特定行业（制造业、零售、医疗等），添加行业相关的公式模板：

```
backend/data/docs/
├── manufacturing_shift_patterns.txt   ← 制造业三班倒模式
├── retail_holiday_premiums.txt        ← 零售业节假日补贴
└── healthcare_overtime_rules.txt      ← 医疗行业加班规定
```

---

## 操作步骤

准备好文件后执行：

```bash
# 1. 把文件放到对应目录
#    公式 → backend/data/samples/*.ff + *.json
#    文档 → backend/data/docs/*.txt
#    DBI  → backend/data/dbi_registry/time_labor_dbis.json

# 2. 重新导入知识库
cd backend
source venv/bin/activate
python -m app.scripts.seed_knowledge_base

# 3. 重启后端
uvicorn app.main:app --reload --port 8000
```

## 文件格式要求

### .ff 公式文件
- 纯文本，UTF-8 编码
- 每个文件一个完整公式
- 建议在文件开头加注释说明用途

### .json 元数据文件（可选）
- 与 .ff 文件同名，后缀改为 .json
- 格式：`{"formula_type": "TIME_LABOR", "use_case": "overtime", "complexity": "simple|medium|complex"}`

### .txt 文档文件
- 纯文本，UTF-8 编码
- 放在 `backend/data/docs/` 目录
- 内容不限：规则说明、示例、最佳实践等

### DBI JSON
- 数组格式，每项：`{"name": "DBI_NAME", "data_type": "NUMBER|TEXT|DATE", "module": "TIME_LABOR", "description": "说明"}`

## 质量提升预期

| 知识库规模 | 预期效果 |
|-----------|---------|
| 5 个样例（当前） | 基础生成能力，语法大致正确 |
| 20-30 个真实公式 | 显著提升，风格一致，业务逻辑准确 |
| 50+ 公式 + 业务文档 | 接近专家水平，能处理复杂场景 |
| 100+ 公式 + 完整 DBI + 业务规则 | 生产级质量，可直接用于实际部署 |
