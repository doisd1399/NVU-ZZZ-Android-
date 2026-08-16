import fs from 'fs';

let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

// replace createContract
const createContractRegex = /const createContract = async \((.*?)\) => \{([\s\S]*?)await addDoc\(collection\(db, "contratos"\), \{([\s\S]*?)\}\);\n    \} catch \(e\) \{/g;
let found = false;
content = content.replace(createContractRegex, (match, p1, p2, p3) => {
  found = true;
  return `const createContract = async (${p1}) => {${p2}
      const rawPayload = {${p3}};
      
      const cleanPayload = { ...rawPayload };
      Object.keys(cleanPayload).forEach(key => {
        if (cleanPayload[key] === undefined) {
          cleanPayload[key] = null;
        }
      });

      await addDoc(collection(db, "contratos"), cleanPayload);
    } catch (e) {`;
});

console.log("createContract fixed:", found);

fs.writeFileSync('src/context/AppContext.tsx', content);
