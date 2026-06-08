package ru.atum.alfresco.ecm.domain.schema;

import org.alfresco.repo.admin.patch.impl.SchemaUpgradeScriptPatch;
import org.alfresco.util.PropertyCheck;
import org.alfresco.util.schemacomp.SchemaDifferenceHelper;

import java.util.Collections;
import java.util.List;

/**
 * Registers a list of create scripts.
 *
 * @author Derek Hulley
 * @since 4.2
 * <p>
 * modified by sgsmirnov: выпиливание activiti.
 */
public class SchemaBootstrapRegistration {
    private SchemaBootstrap schemaBootstrap;
    private List<String> preCreateScriptUrls;
    private List<String> postCreateScriptUrls;
    private List<SchemaUpgradeScriptPatch> postUpdateScriptPatches;
    private SchemaDifferenceHelper differenceHelper;

    public SchemaBootstrapRegistration() {
        this.preCreateScriptUrls = Collections.emptyList();
        this.postCreateScriptUrls = Collections.emptyList();
        this.postUpdateScriptPatches = Collections.emptyList();
    }

    /**
     * @param schemaBootstrap the component with which to register the URLs
     */
    public void setSchemaBootstrap(SchemaBootstrap schemaBootstrap) {
        this.schemaBootstrap = schemaBootstrap;
    }

    /**
     * @param differenceHelper the component with which to register upgrade script pacthes
     */
    public void setDifferenceHelper(SchemaDifferenceHelper differenceHelper) {
        this.differenceHelper = differenceHelper;
    }

    /**
     * @param preCreateScriptUrls a list of schema create URLs that will be registered in order.
     * @see SchemaBootstrap#addPreCreateScriptUrl(String)
     */
    public void setPreCreateScriptUrls(List<String> preCreateScriptUrls) {
        this.preCreateScriptUrls = preCreateScriptUrls;
    }

    /**
     * @param preCreateScriptUrls a list of schema create URLs that will be registered in order.
     * @see SchemaBootstrap#addPostCreateScriptUrl(String)
     */
    public void setPostCreateScriptUrls(List<String> preCreateScriptUrls) {
        this.postCreateScriptUrls = preCreateScriptUrls;
    }

    /**
     * @param postUpdateScriptPatches a list of schema upgade script patches to execute after Hibernate patching
     * @see SchemaBootstrap#addPostUpdateScriptPatch(org.alfresco.repo.admin.patch.impl.SchemaUpgradeScriptPatch)
     */
    public void setPostUpdateScriptPatches(List<SchemaUpgradeScriptPatch> postUpdateScriptPatches) {
        this.postUpdateScriptPatches = postUpdateScriptPatches;
    }

    /**
     * Registers all the necessary scripts and patches with the {@link SchemaBootstrap}.
     */
    public void register() {
        PropertyCheck.mandatory(this, "schemaBootstrap", schemaBootstrap);
        PropertyCheck.mandatory(this, "preCreateScriptUrls", preCreateScriptUrls);
        PropertyCheck.mandatory(this, "postCreateScriptUrls", postCreateScriptUrls);
        PropertyCheck.mandatory(this, "postUpdateScriptPatches", postUpdateScriptPatches);

        for (String preCreateScriptUrl : preCreateScriptUrls) {
            schemaBootstrap.addPreCreateScriptUrl(preCreateScriptUrl);
        }
        for (String postCreateScriptUrl : postCreateScriptUrls) {
            schemaBootstrap.addPostCreateScriptUrl(postCreateScriptUrl);
        }
        for (SchemaUpgradeScriptPatch postUpdateScriptPatch : postUpdateScriptPatches) {
            schemaBootstrap.addPostUpdateScriptPatch(postUpdateScriptPatch);
            differenceHelper.addUpgradeScriptPatch(postUpdateScriptPatch);
        }
    }
}
