export type OperationalRole = "admin" | "driver";

type MembershipLike = {
  companyId?: unknown;
  role?: unknown;
  roles?: unknown;
} | null | undefined;

type UserLike = {
  companyId?: unknown;
  role?: unknown;
  roles?: unknown;
  memberships?: Record<string, { role?: unknown; roles?: unknown } | undefined>;
} | null | undefined;

const isOperationalRole = (value: unknown): value is OperationalRole =>
  value === "admin" || value === "driver";

const collectRoles = (value: unknown, target: Set<OperationalRole>) => {
  if (!Array.isArray(value)) return;
  value.forEach((role) => {
    if (isOperationalRole(role)) target.add(role);
  });
};

/**
 * Resolves the effective roles of a company membership while preserving
 * compatibility with legacy documents that used `role`, omitted `roles`, or
 * stored an empty/invalid roles array.
 *
 * A companyMembers document represents a driver relationship by default. This
 * mirrors the legacy NVU behavior and prevents the profile selector from
 * showing Motorista while the protected route rejects the same membership.
 */
export function resolveMembershipRoles(
  membership: MembershipLike,
  user?: UserLike,
): OperationalRole[] {
  const resolved = new Set<OperationalRole>();

  collectRoles(membership?.roles, resolved);
  if (isOperationalRole(membership?.role)) resolved.add(membership.role);

  const companyId =
    typeof membership?.companyId === "string" ? membership.companyId : null;
  const legacyMembership = companyId ? user?.memberships?.[companyId] : undefined;
  collectRoles(legacyMembership?.roles, resolved);
  if (isOperationalRole(legacyMembership?.role)) {
    resolved.add(legacyMembership.role);
  }

  // O perfil corporativo também pode abrir o perfil operacional de motorista
  // dentro da mesma empresa. Documentos antigos às vezes registraram apenas
  // "admin", embora a interface sempre tenha oferecido os dois perfis.
  if (resolved.has("admin")) resolved.add("driver");

  // Legacy companyMembers records without role metadata were driver links.
  // Resolve that before account-wide roles so an administrator account does
  // not accidentally turn a malformed driver membership into admin access.
  if (resolved.size === 0 && membership) resolved.add("driver");

  // Funções globais são compatibilidade de contas antigas de empresa única.
  // Quando há vínculo, elas só podem complementar a empresa exata indicada no
  // cadastro do usuário, evitando autorização cruzada entre empresas.
  const sameLegacyCompany =
    Boolean(companyId) &&
    typeof user?.companyId === "string" &&
    user.companyId === companyId;
  if (resolved.size === 0 || sameLegacyCompany) {
    collectRoles(user?.roles, resolved);
    if (isOperationalRole(user?.role)) resolved.add(user.role);
  }

  if (resolved.has("admin")) resolved.add("driver");

  return Array.from(resolved);
}

export function membershipHasRole(
  membership: MembershipLike,
  role: OperationalRole,
  user?: UserLike,
): boolean {
  return resolveMembershipRoles(membership, user).includes(role);
}
