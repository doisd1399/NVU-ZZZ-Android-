import fs from 'fs';

let content = fs.readFileSync('src/pages/admin/fleet/ContractsTab.tsx', 'utf8');

// Add Folder, FolderOpen to imports
content = content.replace(
  'FolderPlus,',
  'FolderPlus,\n  Folder,\n  FolderOpen,'
);

// We replace FolderTree with Folder or FolderOpen dynamically.
// Note: we can't do it dynamically inside the regex replacement easily. Let's just use `!isCollapsed ? <FolderOpen .../> : <Folder .../>`
// The current code has: <FolderTree size={16} className={group.seq.id === "none" ? "text-gray-400" : ""} />

content = content.replace(
  '<FolderTree size={16} className={group.seq.id === "none" ? "text-gray-400" : ""} />',
  '{!isCollapsed ? <FolderOpen size={16} className={group.seq.id === "none" ? "text-gray-400" : "fill-blue-500/20 text-blue-600"} /> : <Folder size={16} className={group.seq.id === "none" ? "text-gray-400" : "fill-blue-500/20 text-blue-600"} />}'
);

content = content.replace(
  '<FolderTree size={16} className="text-gray-400" />',
  '<Folder size={16} className="text-gray-400" />'
);

fs.writeFileSync('src/pages/admin/fleet/ContractsTab.tsx', content);
