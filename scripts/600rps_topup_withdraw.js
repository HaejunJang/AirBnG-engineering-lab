import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = "http://localhost:9000/AirBnG";
const TOKEN = __ENV.TOKEN;
const ACCOUNT_ID = 1;
const TOPUP_AMOUNT = 3000;
const WITHDRAW_AMOUNT = 1000;
const TOPUP_RATE = Number(__ENV.TOPUP_RATE || 300);
const WITHDRAW_RATE = Number(__ENV.WITHDRAW_RATE || 300);

export const options = {
    scenarios: {
        topup_load: {
            executor: "constant-arrival-rate",
            exec: "topup",
            rate: TOPUP_RATE,
            timeUnit: "1s",
            duration: "60s",
            preAllocatedVUs: 300,
            maxVUs: 2000,
        },
        withdraw_load: {
            executor: "constant-arrival-rate",
            exec: "withdraw",
            rate: WITHDRAW_RATE,
            timeUnit: "1s",
            duration: "60s",
            preAllocatedVUs: 300,
            maxVUs: 2000,
            startTime: "0s",
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<800"],
        dropped_iterations: ["count==0"],
    },
};

function uuidv4() {
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        const v = c === "x" ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
}

function headers() {
    return {
        Authorization: `Bearer ${TOKEN}`,
        "Content-Type": "application/json",
        "Idempotency-Key": uuidv4(),
    };
}

export function topup() {
    const res = http.post(
        `${BASE_URL}/wallet/me/topup`,
        JSON.stringify({ accountId: ACCOUNT_ID, balance: TOPUP_AMOUNT }),
        { headers: headers() }
    );
    check(res, { "topup 2xx": (r) => r.status >= 200 && r.status < 300 });
    sleep(0.001);
}

export function withdraw() {
    const res = http.post(
        `${BASE_URL}/wallet/me/withdraw`,
        JSON.stringify({ accountId: ACCOUNT_ID, amount: WITHDRAW_AMOUNT }),
        { headers: headers() }
    );
    check(res, { "withdraw 2xx": (r) => r.status >= 200 && r.status < 300 });
    sleep(0.001);
}