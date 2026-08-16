import http from "http";

const boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
const payload = `--${boundary}\r
Content-Disposition: form-data; name="image"; filename="t.txt"\r
Content-Type: text/plain\r
\r
test\r
--${boundary}--\r
`;

const req = http.request(
  {
    hostname: "localhost",
    port: 3000,
    path: "/api/upload-drive",
    method: "POST",
    headers: {
      "Content-Type": `multipart/form-data; boundary=${boundary}`,
      "Content-Length": Buffer.byteLength(payload),
    },
  },
  (res) => {
    let chunks = "";
    res.on("data", (d) => (chunks += d.toString()));
    res.on("end", () => console.log("Response:", chunks));
  },
);

req.on("error", console.error);
req.write(payload);
req.end();
