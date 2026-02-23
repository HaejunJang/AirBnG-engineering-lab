import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

const BASE_URL = "http://localhost:9000/AirBnG";
const TOKEN = __ENV.TOKEN;
const ACCOUNT_ID = 1;
const TOPUP_AMOUNT = 3000;


const successRate = new Rate("topup_success_rate");     // 2xx 비율
const lock429Rate = new Rate("topup_429_rate");         // 429 비율
const err5xxRate = new Rate("topup_5xx_rate");          // 5xx 비율
const successDuration = new Trend("topup_success_duration");

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
        topup_success_rate: ["rate>0.95"],

        topup_5xx_rate: ["rate==0"],

        topup_success_duration: ["p(95)<500"],

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

export default function () {
    const url = `${BASE_URL}/wallet/me/topup`;

    const payload = JSON.stringify({
        accountId: ACCOUNT_ID,
        balance: TOPUP_AMOUNT,
    });

    const res = http.post(url, payload, { headers: headers() });

    const is2xx = res.status >= 200 && res.status < 300;
    const is429 = res.status === 429;
    const is5xx = res.status >= 500 && res.status < 600;

    check(res, {
        "topup 2xx": () => is2xx,
        "topup 429": () => is429,
        "topup 5xx": () => is5xx,
    });

    successRate.add(is2xx);
    lock429Rate.add(is429);
    err5xxRate.add(is5xx);

    if (is2xx) {
        successDuration.add(res.timings.duration);
    }

    sleep(0.1);
}