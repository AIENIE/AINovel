# 注册页

- 路由：`/register`；实现：`src/pages/auth/Register.tsx`。
- 生成一次性 `state` 后跳转 `GET /api/v1/sso/register`；注册规则、验证码和账号资料由统一登录服务处理。
- 回调处理与登录页相同：校验 `state`、兑换授权码、写入 token、回到 `next` 目标。
