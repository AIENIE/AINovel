import { useEffect, useState } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { api } from "@/lib/mock-api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { useToast } from "@/components/ui/use-toast";
import { Coins, CalendarCheck, Gift, Shield, Loader2, ArrowRightLeft } from "lucide-react";

const ProfilePage = () => {
  const { user, refreshProfile } = useAuth();
  const { toast } = useToast();
  
  const [isCheckingIn, setIsCheckingIn] = useState(false);
  const [redeemCode, setRedeemCode] = useState("");
  const [isRedeeming, setIsRedeeming] = useState(false);
  const [convertAmount, setConvertAmount] = useState("");
  const [isConverting, setIsConverting] = useState(false);

  const isCheckedInToday = () => {
    if (!user?.lastCheckIn) return false;
    const last = new Date(user.lastCheckIn).toDateString();
    const today = new Date().toDateString();
    return last === today;
  };

  const handleCheckIn = async () => {
    setIsCheckingIn(true);
    try {
      const res = await api.user.checkIn();
      toast({ 
        title: "签到成功", 
        description: `获得 ${res.points} 积分！当前总余额: ${res.totalCredits ?? res.newTotal}`,
        className: "bg-yellow-50 border-yellow-200 text-yellow-800"
      });
      await refreshProfile();
    } catch (error) {
      toast({ variant: "destructive", title: "签到失败" });
    } finally {
      setIsCheckingIn(false);
    }
  };

  const handleRedeem = async () => {
    if (!redeemCode) return;
    setIsRedeeming(true);
    try {
      const res = await api.user.redeem(redeemCode);
      toast({ 
        title: "兑换成功", 
        description: `获得 ${res.points} 项目积分！当前总余额: ${res.totalCredits ?? res.newTotal}`,
        className: "bg-green-50 border-green-200 text-green-800"
      });
      setRedeemCode("");
      await refreshProfile();
    } catch (error: any) {
      toast({ variant: "destructive", title: "兑换失败", description: error.message });
    } finally {
      setIsRedeeming(false);
    }
  };

  const handleConvert = async () => {
    const amount = Number(convertAmount);
    if (!Number.isFinite(amount) || amount <= 0) return;
    setIsConverting(true);
    try {
      const idempotencyKey = `convert-${Date.now()}-${amount}`;
      const res = await api.user.convertPublicToProject(amount, idempotencyKey);
      toast({
        title: "兑换成功",
        description: `已兑换 ${res.amount} 通用积分为项目积分`,
        className: "bg-blue-50 border-blue-200 text-blue-800"
      });
      setConvertAmount("");
      await refreshProfile();
    } catch (error: any) {
      toast({ variant: "destructive", title: "兑换失败", description: error.message });
    } finally {
      setIsConverting(false);
    }
  };

  if (!user) return null;

  return (
    <div className="container max-w-5xl mx-auto py-8 space-y-8">
      <h1 className="text-3xl font-bold">个人中心</h1>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* User Info Card */}
        <Card className="md:col-span-1">
          <CardHeader className="text-center">
            <div className="mx-auto mb-4">
              <Avatar className="h-24 w-24">
                <AvatarImage src={user.avatar} />
                <AvatarFallback className="text-2xl">{user.username.slice(0, 2).toUpperCase()}</AvatarFallback>
              </Avatar>
            </div>
            <CardTitle>{user.username}</CardTitle>
            <CardDescription>{user.email}</CardDescription>
            <div className="mt-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-primary/10 text-primary">
              {user.role === 'admin' ? '管理员' : '普通用户'}
            </div>
          </CardHeader>
        </Card>

        {/* Credits & Economy Card */}
        <Card className="md:col-span-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Coins className="h-5 w-5 text-yellow-500" /> 我的资产
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="flex items-center justify-between p-4 bg-muted/30 rounded-lg">
              <div>
                <div className="text-sm text-muted-foreground">项目专属积分</div>
                <div className="text-3xl font-bold text-primary">{user.projectCredits.toLocaleString()}</div>
                <div className="text-xs text-muted-foreground mt-1">1 积分 ≈ 100k Token</div>
              </div>
              <Button 
                size="lg" 
                onClick={handleCheckIn} 
                disabled={isCheckedInToday() || isCheckingIn}
                className={isCheckedInToday() ? "bg-muted text-muted-foreground hover:bg-muted" : "bg-yellow-500 hover:bg-yellow-600 text-white"}
              >
                {isCheckingIn ? <Loader2 className="h-4 w-4 animate-spin" /> : <CalendarCheck className="mr-2 h-4 w-4" />}
                {isCheckedInToday() ? "今日已签到" : "每日签到"}
              </Button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div className="p-3 rounded-md border bg-background">
                <div className="text-xs text-muted-foreground">通用积分（payService）</div>
                <div className="text-xl font-semibold">{user.publicCredits.toLocaleString()}</div>
              </div>
              <div className="p-3 rounded-md border bg-background">
                <div className="text-xs text-muted-foreground">总余额</div>
                <div className="text-xl font-semibold">{user.totalCredits.toLocaleString()}</div>
              </div>
            </div>

            <div className="space-y-2">
              <Label>积分兑换</Label>
              <div className="flex gap-2">
                <Input 
                  placeholder="输入兑换码 (例如 VIP888)" 
                  value={redeemCode}
                  onChange={(e) => setRedeemCode(e.target.value)}
                />
                <Button onClick={handleRedeem} disabled={isRedeeming || !redeemCode}>
                  {isRedeeming ? <Loader2 className="h-4 w-4 animate-spin" /> : <Gift className="mr-2 h-4 w-4" />}
                  兑换
                </Button>
              </div>
            </div>

            <div className="space-y-2">
              <Label>通用积分兑换为项目积分（1:1）</Label>
              <div className="flex gap-2">
                <Input
                  type="number"
                  min={1}
                  placeholder="输入兑换数量"
                  value={convertAmount}
                  onChange={(e) => setConvertAmount(e.target.value)}
                />
                <Button onClick={handleConvert} disabled={isConverting || !convertAmount || Number(convertAmount) <= 0}>
                  {isConverting ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowRightLeft className="mr-2 h-4 w-4" />}
                  兑换
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Security Settings */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Shield className="h-5 w-5" /> 安全设置
          </CardTitle>
        </CardHeader>
        <CardContent className="max-w-md space-y-2 text-sm text-muted-foreground">
          <div>本系统已启用统一登录（SSO）。</div>
          <div>密码/注册/登录相关操作由统一登录服务管理，本服务不再提供修改密码入口。</div>
        </CardContent>
      </Card>
    </div>
  );
};

export default ProfilePage;
