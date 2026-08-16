import fs from "fs";

let adminDash = fs.readFileSync("src/pages/admin/Dashboard.tsx", "utf-8");

// revert online colors
adminDash = adminDash.replaceAll(
  'job.driver!.isOnline ? "text-gray-700 dark:text-gray-300"',
  'job.driver!.isOnline ? "text-green-600"',
);
adminDash = adminDash.replaceAll(
  'job.driver!.isOnline ? "bg-gray-400 dark:bg-gray-500"',
  'job.driver!.isOnline ? "bg-[#32D74B]"',
);

// change card progress background in adminDash
adminDash = adminDash.replaceAll(
  'className="w-full shrink-0 border border-gray-200 dark:border-[#2A2F3A] bg-gray-100 dark:bg-[#09090b] rounded-2xl p-4 flex flex-col gap-3"',
  'className="w-full shrink-0 border border-gray-200 dark:border-[#2A2F3A] bg-gray-50 dark:bg-[#1A1F26] rounded-2xl p-4 flex flex-col gap-3"',
);

fs.writeFileSync("src/pages/admin/Dashboard.tsx", adminDash, "utf-8");

let ops = fs.readFileSync("src/pages/admin/Operations.tsx", "utf-8");

// revert online colors
ops = ops.replaceAll(
  'job.driver!.isOnline ? "text-gray-700 dark:text-gray-300"',
  'job.driver!.isOnline ? "text-green-600"',
);
ops = ops.replaceAll(
  'job.driver!.isOnline ? "bg-gray-400 dark:bg-gray-500"',
  'job.driver!.isOnline ? "bg-[#32D74B]"',
);

// change card progress background in ops
ops = ops.replaceAll(
  'className="w-full shrink-0 border border-gray-200 dark:border-[#2A2F3A] bg-gray-100 dark:bg-[#09090b] rounded-2xl p-4 flex flex-col gap-3"',
  'className="w-full shrink-0 border border-gray-200 dark:border-[#2A2F3A] bg-gray-50 dark:bg-[#1A1F26] rounded-2xl p-4 flex flex-col gap-3"',
);

fs.writeFileSync("src/pages/admin/Operations.tsx", ops, "utf-8");

let driverDash = fs.readFileSync("src/pages/driver/Dashboard.tsx", "utf-8");

// revert online colors
driverDash = driverDash.replaceAll(
  'currentUser?.isOnline ? "bg-gray-400 dark:bg-gray-500"',
  'currentUser?.isOnline ? "bg-[#32D74B]"',
);
driverDash = driverDash.replaceAll(
  "peer-checked:bg-gray-400 dark:peer-checked:bg-gray-500",
  "peer-checked:bg-[#32D74B] dark:peer-checked:bg-[#32D74B]",
);
driverDash = driverDash.replaceAll(
  "bg-gray-400 opacity-75",
  "bg-green-400 opacity-75",
);

fs.writeFileSync("src/pages/driver/Dashboard.tsx", driverDash, "utf-8");

console.log("All fixed");
