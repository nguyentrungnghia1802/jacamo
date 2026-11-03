package org.vnu.sme.goal.dsl.csl.mm;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Concrete CSL model derived from the Classifier/Class/Relationship metamodel. */
public final class CslModel extends StructuralSpecification {
    private final String version;
    private final String name;
    private final List<CslNamedDataType> namedDataTypes;
    private final List<CslGroup> groups;
    private final List<CslRelation> relations;
    private final List<CslCompatibility> compatibilities;
    private final List<CslGeneralization> generalizations;
    private final List<CslInvariant> invariants;

    public CslModel(String version, String name, List<CslEnum> enums, List<CslEntity> entities,
                    List<CslRole> roles, List<CslGroup> groups, List<CslRelation> relations,
                    List<CslOwner> owners, List<CslCompatibility> compatibilities,
                    List<CslGeneralization> generalizations) {
        this(version, name, enums, entities, roles, groups, relations, owners,
                compatibilities, generalizations, List.of(), List.of());
    }

    public CslModel(String version, String name, List<CslEnum> enums, List<CslEntity> entities,
                    List<CslRole> roles, List<CslGroup> groups, List<CslRelation> relations,
                    List<CslOwner> owners, List<CslCompatibility> compatibilities,
                    List<CslGeneralization> generalizations, List<CslInvariant> invariants) {
        this(version, name, enums, entities, roles, groups, relations, owners,
                compatibilities, generalizations, invariants, List.of());
    }

    public CslModel(String version, String name, List<CslEnum> enums, List<CslEntity> entities,
                    List<CslRole> roles, List<CslGroup> groups, List<CslRelation> relations,
                    List<CslOwner> owners, List<CslCompatibility> compatibilities,
                    List<CslGeneralization> generalizations, List<CslInvariant> invariants,
                    List<CslNamedDataType> namedDataTypes) {
        super(name, enums, roles, entities,
                generalizations.stream().filter(x -> roles.stream().anyMatch(r -> r.name().equals(x.specific())))
                        .map(x -> new CslRoleInheritance(x.specific(), x.general())).toList(),
                groups.isEmpty() ? emptyRoot() : groups.get(0));
        this.version = Objects.requireNonNull(version, "version"); this.name = Objects.requireNonNull(name, "name");
        this.namedDataTypes = List.copyOf(namedDataTypes);
        this.groups = List.copyOf(groups); this.relations = List.copyOf(relations);
        this.compatibilities = List.copyOf(compatibilities);
        this.generalizations = List.copyOf(generalizations);
        this.invariants = List.copyOf(invariants);
    }
    public String version() { return version; }
    /** v4 is the revised M1 core, not the legacy runtime/play-chain encoding. */
    public boolean isRevisedCore() { return version.startsWith("v4."); }
    /** Prevent old execution encodings from silently changing v4 semantics. */
    public void requireLegacyRuntime() {
        if (isRevisedCore()) {
            throw new IllegalArgumentException("CSL core v4 supports structural parsing and validation. "
                    + "This runtime backend has not been migrated to same-instance Role inheritance, "
                    + "OrgCtx players and declaration-only containment; it cannot execute a v4 model.");
        }
    }
    public String name() { return name; }
    public List<CslNamedDataType> namedDataTypes() { return namedDataTypes; }
    public List<CslGroup> groups() { return groups; }
    public List<CslGroup> orgContexts() {
        return groups.stream().filter(CslGroup::isOrganizationalContext).toList();
    }
    public List<CslRelation> relations() { return relations; }
    /**
     * Legacy projection for translators that have not yet migrated to
     * Group-to-Role/Group composition relations. Owner is not a metamodel or
     * system-state component in the formal CSL semantics.
     */
    @Deprecated(forRemoval = false)
    public List<CslOwner> owners() {
        java.util.Set<String> groupNames = groups.stream()
                .map(CslGroup::name).collect(java.util.stream.Collectors.toSet());
        return relations.stream()
                .filter(relation -> relation.kind() == RelationKind.COMPOSITION)
                .filter(relation -> groupNames.contains(relation.source().type()))
                .filter(relation -> findRole(relation.target().type()).isPresent()
                        || findGroup(relation.target().type()).isPresent())
                .map(relation -> new CslOwner(relation.source().type(), relation.target().type(),
                        relation.target().multiplicity()))
                .toList();
    }
    public List<CslCompatibility> compatibilities() { return compatibilities; }
    public List<CslGeneralization> generalizations() { return generalizations; }
    public List<CslInvariant> invariants() { return invariants; }
    public Optional<CslRole> findRole(String value) { return roles().stream().filter(x -> x.name().equals(value)).findFirst(); }
    public Optional<CslEntity> findEntity(String value) { return entities().stream().filter(x -> x.name().equals(value)).findFirst(); }
    public Optional<CslGroup> findGroup(String value) { return groups.stream().filter(x -> x.name().equals(value)).findFirst(); }
    public Optional<CslGroup> findOrgContext(String value) {
        return orgContexts().stream().filter(x -> x.name().equals(value)).findFirst();
    }
    private static CslGroup emptyRoot() {
        return new CslGroup("__root__", Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
