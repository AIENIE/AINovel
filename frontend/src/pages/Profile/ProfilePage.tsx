import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useAuth } from "@/contexts/auth-state";
import { api } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { useToast } from "@/components/ui/use-toast";
import { localizedErrorMessage } from "@/lib/error-messages";
import { Coins, Gift, Shield, Loader2, ArrowRightLeft } from "lucide-react";
import { CreditConversionRecord, CreditLedgerItem } from "@/types";

const ProfilePage = () => {
  const { user, refreshProfile } = useAuth();
  const { toast } = useToast();
  const { t } = useTranslation();
  
  const [redeemCode, setRedeemCode] = useState("");
  const [isRedeeming, setIsRedeeming] = useState(false);
  const [convertAmount, setConvertAmount] = useState("");
  const [isConverting, setIsConverting] = useState(false);
  const [ledger, setLedger] = useState<CreditLedgerItem[]>([]);
  const [conversions, setConversions] = useState<CreditConversionRecord[]>([]);
  const [isRecordsLoading, setIsRecordsLoading] = useState(false);

  const loadRecords = async () => {
    setIsRecordsLoading(true);
    try {
      const [ledgerItems, conversionItems] = await Promise.all([
        api.user.listLedger(),
        api.user.listConversionHistory(),
      ]);
      setLedger(ledgerItems || []);
      setConversions(conversionItems || []);
    } catch {
      // ignore silently and keep page usable
    } finally {
      setIsRecordsLoading(false);
    }
  };

  useEffect(() => {
    if (user) {
      loadRecords();
    }
  }, [user]);

  const handleRedeem = async () => {
    if (!redeemCode) return;
    setIsRedeeming(true);
    try {
      const res = await api.user.redeem(redeemCode);
      toast({ 
        title: t("profile.redeemed"),
        description: t("profile.redeemedDesc", { points: res.points, total: res.totalCredits ?? res.newTotal }),
        className: "bg-green-50 border-green-200 text-green-800"
      });
      setRedeemCode("");
      await refreshProfile();
      await loadRecords();
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("profile.redeemFailed"), description: localizedErrorMessage(error, "profile.redeemFailed") });
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
        title: t("profile.redeemed"),
        description: t("profile.convertDesc", { publicBefore: res.publicBefore, publicAfter: res.publicAfter, projectBefore: res.projectBefore, projectAfter: res.projectAfter }),
        className: "bg-blue-50 border-blue-200 text-blue-800"
      });
      setConvertAmount("");
      await refreshProfile();
      await loadRecords();
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("profile.redeemFailed"), description: localizedErrorMessage(error, "profile.redeemFailed") });
    } finally {
      setIsConverting(false);
    }
  };

  if (!user) return null;

  return (
    <div className="container max-w-5xl mx-auto py-8 space-y-8">
      <h1 className="text-3xl font-bold">{t("profile.title")}</h1>

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
              {user.role === 'admin' ? t("profile.roleAdmin") : t("profile.roleUser")}
            </div>
          </CardHeader>
        </Card>

        {/* Credits & Economy Card */}
        <Card className="md:col-span-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Coins className="h-5 w-5 text-yellow-500" /> {t("profile.assets")}
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="p-4 bg-muted/30 rounded-lg">
              <div>
                <div className="text-sm text-muted-foreground">{t("profile.projectCredits")}</div>
                <div className="text-3xl font-bold text-primary">{user.projectCredits.toLocaleString()}</div>
                <div className="text-xs text-muted-foreground mt-1">{t("profile.creditRatio")}</div>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div className="p-3 rounded-md border bg-background">
                <div className="text-xs text-muted-foreground">{t("profile.publicCredits")}</div>
                <div className="text-xl font-semibold">{user.publicCredits.toLocaleString()}</div>
              </div>
              <div className="p-3 rounded-md border bg-background">
                <div className="text-xs text-muted-foreground">{t("profile.totalBalance")}</div>
                <div className="text-xl font-semibold">{user.totalCredits.toLocaleString()}</div>
              </div>
            </div>

            <div className="space-y-2">
              <Label>{t("profile.redeemLabel")}</Label>
              <div className="flex gap-2">
                <Input 
                  placeholder={t("profile.redeemPlaceholder")}
                  value={redeemCode}
                  onChange={(e) => setRedeemCode(e.target.value)}
                />
                <Button onClick={handleRedeem} disabled={isRedeeming || !redeemCode}>
                  {isRedeeming ? <Loader2 className="h-4 w-4 animate-spin" /> : <Gift className="mr-2 h-4 w-4" />}
                  {t("profile.redeem")}
                </Button>
              </div>
            </div>

            <div className="space-y-2">
              <Label>{t("profile.convertLabel")}</Label>
              <div className="flex gap-2">
                <Input
                  type="number"
                  min={1}
                  placeholder={t("profile.convertPlaceholder")}
                  value={convertAmount}
                  onChange={(e) => setConvertAmount(e.target.value)}
                />
                <Button onClick={handleConvert} disabled={isConverting || !convertAmount || Number(convertAmount) <= 0}>
                  {isConverting ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowRightLeft className="mr-2 h-4 w-4" />}
                  {t("profile.redeem")}
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
            <Shield className="h-5 w-5" /> {t("profile.security")}
          </CardTitle>
        </CardHeader>
        <CardContent className="max-w-md space-y-2 text-sm text-muted-foreground">
          <div>{t("profile.ssoEnabled")}</div>
          <div>{t("profile.ssoDesc")}</div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("profile.records")}</CardTitle>
          <CardDescription>{t("profile.recordsDesc")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="space-y-2">
            <div className="text-sm font-medium">{t("profile.ledger")}</div>
            {isRecordsLoading ? (
              <div className="text-sm text-muted-foreground">{t("common.loading")}</div>
            ) : ledger.length === 0 ? (
              <div className="text-sm text-muted-foreground">{t("profile.noRecords")}</div>
            ) : (
              <div className="space-y-2">
                {ledger.slice(0, 10).map((item) => (
                  <div key={item.id} className="rounded-md border p-3 text-sm flex items-center justify-between gap-3">
                    <div className="space-y-1">
                      <div className="font-medium">{item.type}</div>
                      <div className="text-xs text-muted-foreground">{item.description || item.referenceType || "-"}</div>
                    </div>
                    <div className="text-right">
                      <div className={item.delta >= 0 ? "text-green-600 font-semibold" : "text-red-600 font-semibold"}>
                        {item.delta >= 0 ? "+" : ""}{item.delta}
                      </div>
                      <div className="text-xs text-muted-foreground">{t("profile.balanceAfter", { value: item.balanceAfter })}</div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="space-y-2">
            <div className="text-sm font-medium">{t("profile.conversionHistory")}</div>
            {isRecordsLoading ? (
              <div className="text-sm text-muted-foreground">{t("common.loading")}</div>
            ) : conversions.length === 0 ? (
              <div className="text-sm text-muted-foreground">{t("profile.noConversions")}</div>
            ) : (
              <div className="space-y-2">
                {conversions.slice(0, 10).map((item) => (
                  <div key={item.id} className="rounded-md border p-3 text-sm space-y-1">
                    <div className="flex items-center justify-between">
                      <div className="font-medium">{item.orderNo}</div>
                      <div className="text-xs text-muted-foreground">{item.status}</div>
                    </div>
                    <div className="text-xs text-muted-foreground">
                      {t("profile.conversionDetail", { publicBefore: item.publicBefore, publicAfter: item.publicAfter, projectBefore: item.projectBefore, projectAfter: item.projectAfter })}
                    </div>
                    <div className="text-xs text-muted-foreground">
                      {t("profile.conversionMeta", { amount: item.convertedAmount, time: new Date(item.createdAt).toLocaleString() })}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default ProfilePage;
