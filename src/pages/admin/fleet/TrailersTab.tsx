import React, { useState } from "react";
import { useOperationalStore } from "../../../context/AppContext";
import { Card, CardContent } from "../../../components/ui/Card";
import { Button } from "../../../components/ui/Button";
import { isOpenJobStatus } from "../../../lib/jobStatus";
import { Container, Plus, X, Pencil, Trash2, Users } from "lucide-react";

function TrailersTab() {
  const { trailers, addTrailer, updateTrailer, deleteTrailer, jobs } =
    useOperationalStore();
  const [isAdding, setIsAdding] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [paintCode, setPaintCode] = useState("");

  const resetForm = () => {
    setIsAdding(false);
    setEditingId(null);
    setName("");
    setPaintCode("");
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name) return;

    if (editingId) {
      updateTrailer(editingId, { name, paintCode });
    } else {
      addTrailer({ name, paintCode });
    }
    resetForm();
  };

  const handleEdit = (
    id: string,
    currentName: string,
    currentPaintCode?: string,
  ) => {
    setEditingId(id);
    setName(currentName);
    setPaintCode(currentPaintCode || "");
    setIsAdding(true);
  };

  const handleDelete = (id: string) => {
    if (true) {
      deleteTrailer(id);
    }
  };

  const getUsageCount = (trailerId: string) => {
    // Conta quantos motoristas únicos estão usando este reboque em trabalhos pendings ou ativos
    const activeJobs = jobs.filter(
      (j) =>
        j.trailerId === trailerId && isOpenJobStatus(j.status),
    );
    const uniqueDrivers = new Set(activeJobs.map((j) => j.driverId));
    return uniqueDrivers.size;
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-end">
        <Button
          onClick={() => setIsAdding(true)}
          className="gap-1.5 h-9 bg-green-500 hover:bg-green-600 text-[13px] px-4 rounded-lg"
        >
          <Plus size={16} />
          Adicionar Reboque
        </Button>
      </div>

      {isAdding && (
        <Card className="rounded-2xl border border-gray-200 dark:border-[#2A2F3A] shadow-sm dark:shadow-none mb-6 bg-white dark:bg-[#1A1F26] overflow-hidden relative">
          <div className="h-1 w-full absolute top-0 left-0 bg-green-500"></div>
          <CardContent className="p-6">
            <div className="flex justify-between items-center mb-6">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-[#fafafa]">
                Novo Reboque
              </h2>
              <button
                onClick={() => setIsAdding(false)}
                className="text-gray-400 hover:text-gray-900 dark:text-[#fafafa] dark:hover:text-[#f4f4f5]"
              >
                <X size={20} />
              </button>
            </div>
            <form
              onSubmit={handleSubmit}
              className="grid grid-cols-1 md:grid-cols-2 gap-4"
            >
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-gray-700 dark:text-[#d4d4d8] mb-1">
                  Tipo / Nome do Reboque
                </label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Ex: Baú Seco 30m³"
                  className="w-full bg-gray-50 dark:bg-[#1A1F26] border border-gray-200 dark:border-[#2A2F3A] rounded-xl px-4 py-3 text-gray-900 dark:text-[#fafafa] focus:outline-none focus:ring-2 focus:ring-green-500 focus:bg-white dark:focus:bg-[#0a0a0b] transition-colors"
                  required
                />
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-gray-700 dark:text-[#d4d4d8] mb-1">
                  Código da Pintura{" "}
                  <span className="text-gray-400 font-normal">(Opcional)</span>
                </label>
                <input
                  type="text"
                  value={paintCode}
                  onChange={(e) => setPaintCode(e.target.value)}
                  placeholder="Ex: #00FF00 ou Verde Folha"
                  className="w-full bg-gray-50 dark:bg-[#1A1F26] border border-gray-200 dark:border-[#2A2F3A] rounded-xl px-4 py-3 text-gray-900 dark:text-[#fafafa] focus:outline-none focus:ring-2 focus:ring-green-500 focus:bg-white dark:focus:bg-[#0a0a0b] transition-colors"
                />
              </div>
              <div className="md:col-span-2 flex justify-end gap-3 mt-4">
                <Button
                  type="button"
                  variant="ghost"
                  onClick={resetForm}
                  className="text-gray-600 dark:text-[#d4d4d8]"
                >
                  Cancelar
                </Button>
                <Button
                  type="submit"
                  className="bg-green-600 dark:bg-green-600 hover:bg-green-700 text-white shadow-sm dark:shadow-none border-none"
                >
                  {editingId ? "Salvar Alterações" : "Salvar Reboque"}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {trailers.length === 0 ? (
        <div className="text-center py-12 bg-white dark:bg-[#1A1F26] rounded-2xl border border-gray-200 dark:border-[#2A2F3A] border-dashed">
          <div className="mx-auto w-16 h-16 bg-gray-50 dark:bg-[#1A1F26] rounded-full flex items-center justify-center text-gray-400 mb-4">
            <Container size={24} />
          </div>
          <h3 className="text-lg font-semibold text-gray-900 dark:text-[#fafafa]">
            Nenhum reboque encontrado
          </h3>
          <p className="text-gray-500 dark:text-[#a1a1aa] mt-1 max-w-sm mx-auto">
            Adicione um novo reboque à sua frota.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3 md:gap-4 lg:gap-6">
          {trailers.map((trailer) => {
            const usageCount = getUsageCount(trailer.id);
            return (
              <Card
                key={trailer.id}
                className="rounded-xl border border-gray-200 dark:border-[#2A2F3A] shadow-none relative overflow-hidden group hover:shadow-sm dark:shadow-none transition-shadow dark:shadow-none bg-white dark:bg-[#1A1F26]"
              >
                <CardContent className="p-3 md:p-4">
                  <div className="flex justify-between items-start mb-3">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-gray-50 dark:bg-[#1A1F26] text-gray-600 dark:text-[#d4d4d8] rounded-lg flex items-center justify-center flex-shrink-0">
                        <Container size={20} />
                      </div>
                      <div>
                        <h3 className="font-semibold text-gray-900 dark:text-[#fafafa] text-[14px] leading-tight mb-1">
                          {trailer.name}
                        </h3>
                        <div className="flex items-center gap-1.5 text-[12px] text-gray-500 dark:text-[#a1a1aa]">
                          <Users size={12} />
                          <span>
                            Em uso por {usageCount} motorista
                            {usageCount !== 1 && "s"}
                          </span>
                        </div>
                      </div>
                    </div>
                    <div className="flex gap-1">
                      <button
                        onClick={() =>
                          handleEdit(
                            trailer.id,
                            trailer.name,
                            trailer.paintCode,
                          )
                        }
                        className="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 dark:bg-blue-500/10 dark:border-blue-500/20 rounded-lg transition-colors"
                        title="Editar"
                      >
                        <Pencil size={16} />
                      </button>
                      <button
                        onClick={() => handleDelete(trailer.id)}
                        className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 dark:bg-red-500/10 dark:border-red-500/20 rounded-lg transition-colors"
                        title="Excluir"
                      >
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default React.memo(TrailersTab);
