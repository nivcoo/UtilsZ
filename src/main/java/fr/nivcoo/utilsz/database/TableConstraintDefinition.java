package fr.nivcoo.utilsz.database;

public class TableConstraintDefinition {
    private final String constraint;

    public TableConstraintDefinition(String constraint) {
        this.constraint = constraint;
    }

    public String getConstraint() {
        return constraint;
    }
}
