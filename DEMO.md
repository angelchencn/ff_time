# FF Time Demo Script

## Demo Flow Overview

1. **AI 生成** — 自然语言 → Fast Formula（展示核心能力）
2. **AI 修改** — 在已有公式上迭代修改（展示上下文记忆）
3. **验证** — 语法检查 + 语义检查（展示编译器能力）
4. **模拟执行** — 输入数据 → 查看计算结果（展示模拟器）
5. **DBI 查询** — 展示数据库项参考
6. **复杂场景** — 展示高级语法支持

---

## Demo Case 1: Basic Overtime（入门级，2分钟）

**目的：** 展示从零开始用自然语言生成公式

**Chat 输入：**
```
Write a formula to calculate overtime pay. Regular hours threshold is 40, overtime rate is 1.5x.
```

**预期效果：**
- AI 生成完整的 overtime 公式
- 代码自动填入编辑器
- 右侧 Validate tab 显示 "VALID"
- Monaco Editor 语法高亮显示

**然后切到 Simulate tab：**
- 输入 `HOURS_WORKED = 50`, `REGULAR_RATE = 25`
- 点 Run Simulation
- 展示结果：regular_pay = 1000, overtime_pay = 375, total_pay = 1375

---

## Demo Case 2: 迭代修改（展示对话记忆，2分钟）

**在 Case 1 基础上继续 Chat：**

**第一轮修改：**
```
Change the overtime multiplier to 2.0 for double time
```

**预期：** AI 在原有公式基础上把 1.5 改成 2.0，不是重新生成

**第二轮修改：**
```
Add a holiday check. If HOLIDAY_FLAG is 'Y', pay should be 3x regardless of hours.
```

**预期：** AI 在现有公式上增加 holiday 逻辑，加入 IF HOLIDAY_FLAG 判断

**演示要点：** 每次修改都保留之前的逻辑，展示 AI 理解上下文

---

## Demo Case 3: California Double Overtime（复杂场景，3分钟）

**Chat 输入：**
```
Write a California-style overtime formula:
- First 8 hours: regular rate
- 8 to 12 hours: 1.5x rate
- Over 12 hours: 2x rate (double time)
```

**预期生成的公式类似：**
```
/* California Style Double Overtime */
DEFAULT FOR HOURS_WORKED IS 0
DEFAULT FOR REGULAR_RATE IS 0

regular_hours = 0
overtime_hours = 0
double_overtime_hours = 0

IF HOURS_WORKED <= 8 THEN
    regular_hours = HOURS_WORKED
ELSIF HOURS_WORKED <= 12 THEN
    regular_hours = 8
    overtime_hours = HOURS_WORKED - 8
ELSE
    regular_hours = 8
    overtime_hours = 4
    double_overtime_hours = HOURS_WORKED - 12
END IF

total_pay = (regular_hours * REGULAR_RATE) +
            (overtime_hours * REGULAR_RATE * 1.5) +
            (double_overtime_hours * REGULAR_RATE * 2)

RETURN total_pay
```

**Simulate 测试用例：**

| HOURS_WORKED | REGULAR_RATE | Expected total_pay |
|---|---|---|
| 6 | 30 | 180 (6 × 30) |
| 10 | 30 | 330 (8×30 + 2×30×1.5) |
| 14 | 30 | 480 (8×30 + 4×30×1.5 + 2×30×2) |

---

## Demo Case 4: Shift Differential（展示文本比较，2分钟）

**Chat 输入：**
```
Create a shift differential formula. Night shift gets 15% premium, evening shift gets 8% premium, day shift gets no premium. Use SHIFT_TYPE variable.
```

**预期：** 生成 IF/ELSIF/ELSE 判断 SHIFT_TYPE 的公式

**Simulate：**
- SHIFT_TYPE = NIGHT, HOURS_WORKED = 8, REGULAR_RATE = 25 → premium = 30
- SHIFT_TYPE = DAY, HOURS_WORKED = 8, REGULAR_RATE = 25 → premium = 0

---

## Demo Case 5: 故意写错，展示验证（2分钟）

**手动在编辑器中输入有错误的代码：**

```
DEFAULT FOR hours IS 0

IF hours > 40 THEN
    overtime = hours - 40
    pay = overtime * unknown_rate
END IF

RETURN pay
```

**展示 Validate tab 会报：**
- ERROR: semantic — `unknown_rate` 未声明
- WARNING: rule — 缺少对 `HOURS_WORKED` DBI 的引用（如果有 overtime 相关 output）

**然后在 Chat 中输入：**
```
Fix the errors in this formula
```

**预期：** AI 看到编辑器中的代码和错误，自动修复（添加 DEFAULT FOR unknown_rate 等）

---

## Demo Case 6: Weekly Hours Cap with Warning（实用场景，2分钟）

**Chat 输入：**
```
Write a formula that caps weekly hours at 60. If employee works more than 60 hours, cap at 60 and set a warning flag. Calculate pay based on capped hours.
```

**Simulate：**
- HOURS_WORKED = 45, REGULAR_RATE = 20 → total_pay = 900, warning = 'N'
- HOURS_WORKED = 70, REGULAR_RATE = 20 → total_pay = 1200 (capped at 60), warning = 'Y'

---

## Demo Case 7: Explain 功能（1分钟）

**在编辑器中有任意公式后：**
- 点右侧 **Explain** tab
- 点 "Explain Formula" 按钮
- AI 用自然语言解释公式的业务逻辑

**演示要点：** 对于不熟悉 Fast Formula 的 HR 顾问，这个功能帮助理解现有公式

---

## Demo Case 8: DBI 参考（30秒）

- 点右侧 **DBIs** tab
- 展示 65 个数据库项的表格
- 搜索 "hours" → 过滤出相关 DBI
- 搜索 "schedule" → 展示排班相关 DBI
- 展示 Module 分类：TIME_LABOR / PERSON / PAYROLL

---

## Demo Tips

### 准备工作
1. 确保后端运行：`cd backend && source venv/bin/activate && uvicorn app.main:app --reload --port 8000`
2. 确保前端运行：`cd frontend && npm run dev`
3. 打开 http://localhost:5173
4. 提前跑一次 Case 1 确认 API 连通

### 演示节奏
- 每个 Case 之间点 **New** 按钮清空
- 先展示简单场景（Case 1-2），再展示复杂场景（Case 3-4）
- Case 5（错误修复）是亮点，放在中间
- Case 7-8（Explain + DBI）可以穿插在任何时候

### 如果 AI 生成的代码有语法错误
这本身就是一个 demo point — 展示验证器能立即捕获错误，然后可以让 AI 修复

### 关键卖点话术
- "用自然语言描述需求，AI 直接生成 Fast Formula"
- "在已有公式上迭代修改，AI 理解上下文"
- "内置编译器实时语法检查，不需要部署到 Oracle 就能验证"
- "模拟执行可以直接测试，不需要 Oracle 环境"
- "65 个 Time & Labor DBI 参考，随时查阅"
