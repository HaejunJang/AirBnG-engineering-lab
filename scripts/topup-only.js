import http from "k6/http";
import { check, sleep } from "k6";


const BASE_URL = "http://localhost:9000/AirBnG";
const TOKEN = __ENV.TOKEN;
const ACCOUNT_ID = 1;
const TOPUP_AMOUNT = 3000;

export const options = {
    scenarios: {
        topup_only: {
            executor: "ramping-vus",
            startVUs: 0,
            stages: [
                { duration: "10s", target: 10 },
                { duration: "20s", target: 30 },
                { duration: "20s", target: 50 },
                { duration: "10s", target: 0 },
            ],
            gracefulRampDown: "5s",
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<500"],
    },
};

// UUID 생성
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


export default function () {
    const url = `${BASE_URL}/wallet/me/topup`;

    const payload = JSON.stringify({
        accountId: ACCOUNT_ID,
        balance: TOPUP_AMOUNT,
    });

    const res = http.post(url, payload, { headers: headers() });

    check(res, {
        "topup 2xx": (r) => r.status >= 200 && r.status < 300,
    });

    sleep(0.1);
}