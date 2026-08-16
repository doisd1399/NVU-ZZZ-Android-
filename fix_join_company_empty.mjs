import fs from 'fs';

let content = fs.readFileSync('src/pages/driver/JoinCompany.tsx', 'utf8');

const emptyState = `{filteredCompanies.length === 0 ? (
          <div className="text-center py-12 px-4 rounded-2xl border border-dashed border-gray-200 dark:border-[#2A2F3A] bg-gray-50 dark:bg-[#1A1F26]">
            <Search size={32} className="mx-auto text-gray-300 mb-3" />
            <p className="text-[15px] font-medium text-gray-900 dark:text-[#fafafa]">
              Nenhuma empresa encontrada
            </p>
            <p className="text-sm text-gray-500 dark:text-[#a1a1aa] mt-1">
              Verifique a ortografia ou tente outro termo.
            </p>
          </div>
        )`;

const newEmptyState = `{!selectedSimulatorId ? (
          <div className="text-center py-12 px-4 rounded-2xl border border-dashed border-gray-200 dark:border-[#2A2F3A] bg-gray-50 dark:bg-[#1A1F26]">
            <Building2 size={32} className="mx-auto text-gray-300 mb-3" />
            <p className="text-[15px] font-medium text-gray-900 dark:text-[#fafafa]">
              Selecione um simulador
            </p>
            <p className="text-sm text-gray-500 dark:text-[#a1a1aa] mt-1">
              Escolha um simulador acima para listar as empresas disponíveis.
            </p>
          </div>
        ) : filteredCompanies.length === 0 ? (
          <div className="text-center py-12 px-4 rounded-2xl border border-dashed border-gray-200 dark:border-[#2A2F3A] bg-gray-50 dark:bg-[#1A1F26]">
            <Search size={32} className="mx-auto text-gray-300 mb-3" />
            <p className="text-[15px] font-medium text-gray-900 dark:text-[#fafafa]">
              Nenhuma empresa encontrada
            </p>
            <p className="text-sm text-gray-500 dark:text-[#a1a1aa] mt-1">
              Verifique a ortografia ou tente outro termo.
            </p>
          </div>
        )`;

content = content.replace(emptyState, newEmptyState);
fs.writeFileSync('src/pages/driver/JoinCompany.tsx', content);
