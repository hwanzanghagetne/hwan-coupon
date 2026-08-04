import { requestJson } from "./http";

const baseCouponTemplate = {
  discountType: "FIXED",
  discountValue: 3000,
  minOrderAmount: 10000,
  issueStartTime: null,
  issueEndTime: null,
  expiredAt: "2026-12-31T23:59:59"
};

export async function loginAdmin({ id, password }) {
  const payload = await requestJson("POST", "/api/members/login", { email: id, password });
  const data = payload?.data ?? payload;
  return { status: payload?.status ?? 200, admin: data?.email ?? id, role: data?.role ?? "UNKNOWN" };
}

export async function createCouponApi({ name, qty, issueType }) {
  const payload = await requestJson("POST", "/api/coupons", {
    ...baseCouponTemplate,
    name,
    issueType,
    totalQuantity: issueType === "FIRST_COME" ? qty : null
  });
  const data = payload?.data ?? payload;
  return {
    status: payload?.status ?? 201,
    couponId: data?.id,
    name: data?.name ?? name,
    quantity: data?.totalQuantity ?? (issueType === "FIRST_COME" ? qty : null),
    issueType: data?.issueType ?? issueType,
    createdAt: data?.createdAt ?? new Date().toISOString().slice(0, 19).replace("T", " ")
  };
}

export async function listCouponsApi() {
  const payload = await requestJson("GET", "/api/coupons");
  const data = payload?.data ?? payload;
  const rows = Array.isArray(data?.content) ? data.content : Array.isArray(data) ? data : [];
  return rows.map((coupon) => ({
    id: coupon.id,
    name: coupon.name,
    qty: coupon.totalQuantity ?? "-",
    createdAt: coupon.createdAt ?? "-",
    status: coupon.status,
    issueType: coupon.issueType
  }));
}

export async function runFirstComeApi({ couponId, concurrency, threads }) {
  const payload = await requestJson("POST", "/api/demo/first-come", {
    couponId,
    requestUserCount: concurrency,
    threadCount: threads
  });
  const data = payload?.data ?? payload;
  return {
    status: payload?.status ?? 200,
    couponId: data?.couponId ?? couponId,
    params: { concurrency, threads },
    result: {
      successCount: data?.successCount ?? 0,
      exhaustedCount: data?.exhaustedCount ?? 0,
      duplicateCount: data?.duplicateCount ?? 0,
      otherFailureCount: data?.otherFailureCount ?? 0,
      issuedRows: data?.issuedRows ?? 0,
      issuedQuantity: data?.issuedQuantity ?? 0,
      durationMs: data?.durationMs ?? 0
    }
  };
}

export async function runBatchApi({ couponId, users }) {
  const payload = await requestJson("POST", "/api/demo/batch", { couponId, userCount: users });
  const data = payload?.data ?? payload;
  return {
    status: payload?.status ?? 200,
    couponId: data?.couponId ?? couponId,
    batchId: data?.batchId,
    userCount: data?.userCount ?? users,
    batchStatus: data?.batchStatus,
    completed: data?.completed,
    issuedRows: data?.issuedRows ?? 0,
    issuedQuantity: data?.issuedQuantity ?? 0,
    durationMs: data?.durationMs ?? 0
  };
}

export async function lookupBatchApi(batchId) {
  const payload = await requestJson("GET", `/api/demo/batch-summary?batchId=${batchId}`);
  const data = payload?.data ?? payload;
  return {
    status: payload?.status ?? 200,
    batchId: data?.batchId ?? batchId,
    couponId: data?.couponId,
    batchStatus: data?.batchStatus ?? "UNKNOWN",
    completed: ["DONE", "FAILED"].includes(data?.batchStatus),
    targetCount: data?.targetCount ?? null,
    completedAt: data?.completedAt ?? null,
    issuedRows: data?.couponIssueCountForCoupon ?? 0,
    issuedQuantity: data?.issuedQuantity ?? 0,
    durationMs: null
  };
}

export async function getCouponSummaryApi(couponId) {
  const payload = await requestJson("GET", `/api/demo/summary?couponId=${couponId}`);
  const data = payload?.data ?? payload;
  return {
    status: payload?.status ?? 200,
    couponId: data?.couponId ?? couponId,
    couponName: data?.couponName,
    totalQuantity: data?.totalQuantity,
    issuedQuantity: data?.issuedQuantity ?? 0,
    couponStatus: data?.couponStatus ?? "UNKNOWN",
    issueType: data?.issueType ?? null,
    couponIssueCount: data?.couponIssueCount ?? 0,
    redisRemainingQuantity: data?.redisRemainingQuantity ?? null
  };
}
