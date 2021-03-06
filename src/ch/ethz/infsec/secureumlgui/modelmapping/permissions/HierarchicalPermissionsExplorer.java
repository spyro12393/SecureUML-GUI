/**
 *
 */
package ch.ethz.infsec.secureumlgui.modelmapping.permissions;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.omg.uml.UmlPackage;
import org.omg.uml.foundation.core.UmlClass;

import ch.ethz.infsec.secureumlgui.ModuleController;
import ch.ethz.infsec.secureumlgui.logging.MultiContextLogger;
import ch.ethz.infsec.secureumlgui.wrapper.ActionWrapper;
import ch.ethz.infsec.secureumlgui.wrapper.PermissionWrapper;
import ch.ethz.infsec.secureumlgui.wrapper.PolicyWrapper;
import ch.ethz.infsec.secureumlgui.wrapper.ResourceWrapper;
import ch.ethz.infsec.secureumlgui.wrapper.RoleWrapper;

/**
 * Explore the permission hierarchy generated by role inheritance and
 * action composition.
 *
 */
public class HierarchicalPermissionsExplorer
{
    MultiContextLogger logger = MultiContextLogger.getDefault();

    private static Logger aLog = Logger.getLogger(HierarchicalPermissionsExplorer.class);

    private static HierarchicalPolicyExplorer policyExplorer = HierarchicalPolicyExplorer.getInstance();

    public enum CHANGES {
        EXPLICIT, INHERITED, IMPLICIT_SUB, IMPLICIT_SUPER, INHERITED_POLIY
    }

    public void collectNonExplicitPermissions(ResourceWrapper resource, PermissionSet permissions) {

        aLog.debug("collectNonExplicitPermissions: (roles: " + permissions.getAllRoleWrappers().size() + ")");

        Date start = new Date();

        //List<PolicyWrapper> policies = policyExplorer.getSortedPolicies();
        List<UmlClass> policies = policyExplorer.getSortedPolicies();
//		PolicyWrapper defaultPolicy = HierarchicalPolicyExplorer.getInstance().getDefaultPolicy();
//
//		if ( defaultPolicy == null ) {
//			policies.add(0, defaultPolicy);
//		}

        for ( UmlClass policyClass : policies) {

            PolicyWrapper policy = new PolicyWrapper(ModuleController.getInstance().getModelMap().getElement(policyClass));

            aLog.debug("collecting directly policy-inherited permissions for resruce " + resource + " and policy: " + policy.getName());
            collectPolicyInheritedPermissions(resource, permissions, policy);

            aLog.debug("collecting directly implicit, from explicit, permissions for resource " + resource + " and policy: " + policy.getName());
            //collect implicit permissions, without taking into account dependencies between implicit and inheritied
            collectImplicitPermissions(resource, permissions, policy);

            aLog.debug("colling directly inherited, from explicit, permissions for resource " + resource + " and policy: " + policy.getName());
            collectInheritedPermissions(resource, permissions, policy);

            aLog.debug("collection directly permission END");
            //
            PermissionSet next, last;

            //create an empty next set
            next = new PermissionSet();
            //for the first run, iterate over all permissions
            aLog.debug("collecting indirect implicit permissions, iterating over all permissions");
            collectImplicitPermissions(policy, resource, permissions, permissions, next);

            while (true) {
                last = next;
                next = new PermissionSet();
                if ( last.getAllRoleWrappers().size() == 0 ) { //nothing changed, finished
                    aLog.debug("recursion did not find any further permissions by collecting implicit permissions... DONE!");
                    break;
                } else {
                    if ( aLog.isDebugEnabled() ) {
                        aLog.debug("starting with search of indirectly inherited permissions, number of roles: " + next.getAllRoleWrappers().size());
                    }
                    collectInheritedPermissions(policy, resource, last, permissions, next );
                }

                last = next;
                next = new PermissionSet();
                if ( last.getAllRoleWrappers().size() == 0 ) { //nothing changed, finished
                    aLog.debug("recursion did not find any further permissions by collecting inherited permissions... DONE!");
                    break;
                } else {
                    if ( aLog.isDebugEnabled() ) {
                        aLog.debug("starting with search of indirectly implicit permissions, number of roles: " + next.getAllRoleWrappers().size());
                    }
                    collectImplicitPermissions(policy, resource, last, permissions, next );
                }
            }

        }

        long duration = new Date().getTime() - start.getTime();
        aLog.debug("needed: " + duration);
    }

    private void collectPolicyInheritedPermissions(ResourceWrapper resource, PermissionSet permissions, PolicyWrapper policy) {
        aLog.debug("collectPolicyInheritedPermissions for policy (" + policy.getName() + ") START");

        Collection refinedBy = policy.getRefinedBy();
        if ( refinedBy != null && refinedBy.size() > 0 ) {
            for ( PolicyWrapper supPol : policy.getRefinedByWrappers() ) {
                aLog.debug("start with policy " + supPol.getName());

                for ( RoleWrapper role : permissions.getAllRoleWrappers() ) {

                    ResourcePermissionsSet resourcePermissions = permissions.getResourcePermissionsSet(role);

                    for (ActionWrapper action : resource.getActionWrapper()) {
                        ActionPermissionSet actionPermissions = resourcePermissions.getPermissions(action);

                        for (PermissionValue permission : actionPermissions.getPermissions(supPol)) {
                            actionPermissions.addPermission(policy, PermissionValue.createInheritedPolicy(permission), CHANGES.INHERITED_POLIY);
                        }
                    }
                }
                aLog.debug("end with policy " + supPol.getName());
            }
        }


        aLog.debug("collectPolicyInheritedPermissions END");
    }

    /**
     * collects all permissions, which are DIRECTLY IMPLICIT from the explicit permissions
     * @param resource
     * @param permissions
     */
    private void collectImplicitPermissions(ResourceWrapper resource, PermissionSet permissions, PolicyWrapper policy)  {
        aLog.debug("collectImplicitPermissions START");
        //PermissionSet superactionResourcePermissions = getExplicitPermissions(resource);
        // for a fixed resource, iterate over ALL roles
        for (RoleWrapper role : permissions.getAllRoleWrappers()) {
            aLog.debug("  role: " + role.getName());
            ResourcePermissionsSet resourcePermissions = permissions.getResourcePermissionsSet(role);
            //iterate over all actions of the fixed resource and get the permissions assigned to this action and role, saved in actionPermissions
            for (ActionWrapper action : resource.getActionWrapper()) {
                aLog.debug("    action: " + action.getName());
                ActionPermissionSet actionPermissions = resourcePermissions.getPermissions(action);
                //iterate over all super actions (i.e., all composite actions of which this action is part of), =>  implicit permission from SUPER actions
                for ( ActionWrapper superAction : getSuperActionWrappersDeep(action) ) {
                    //and get all explicit permissions assigned to this resource (any role and action?)

                    Collection<PermissionValue> superActionPermissions =
                        permissions.getResourcePermissionsSet(role).getPermissions(superAction).getPermissions(policy);
//					Collection<PermissionValue> superActionPermissions =
//							superactionResourcePermissions.getResourcePermissionsSet(role).getPermissions(superAction).getPermissions(policy);
//					if ( aLog.isDebugEnabled() ) {
//						aLog.debug("      superAction: " + superAction.getName() + " with " + superActionPermissions.size() + " permissions");
//					}
                    for (PermissionValue permission : superActionPermissions) {
                        actionPermissions.addPermission(policy, PermissionValue.createImplicite(permission), CHANGES.IMPLICIT_SUPER);
                    }
                }

                //implicit permissions from SUB actions
                if ( action.hasSubActions() && isPermittedImplicitBySubactions ( permissions, role, action, policy ) ) {

                    aLog.debug("IMPLICIT BY SUBACTION: " + role.getName() + " on " + action.getName());

                    PermissionValue composite = PermissionValue.createComposite(null, action, role);

                    actionPermissions.addPermission(policy, composite, CHANGES.IMPLICIT_SUB);
                }
            }
        }
        aLog.debug("collectImplicitPermissions END");
    }

    /**
     * collects all permissions, which are DIRECTLY INHERITED from explicit permissions
     * @param resource
     * @param permissions
     */
    private void collectInheritedPermissions(ResourceWrapper resource, PermissionSet permissions, PolicyWrapper policy) {
        aLog.debug("collectInheritedPermissions START");
        // for a fixed resource, iterate over ALL roles
        for (RoleWrapper role : permissions.getAllRoleWrappers()) {
            aLog.debug("  role: " + role.getName());
            ResourcePermissionsSet resourcePermissions = permissions.getResourcePermissionsSet(role);
            // get super Roles for this role
            Set<RoleWrapper> superRoles = getSuperRoleWrappersDeep(role);
            //iterate over all actions of the fixed resources and get the permissions assigned to this action and role, saved in actionPermissions
            for (ActionWrapper action : resource.getActionWrapper()) {
                aLog.debug("    action: " + action.getName());
                ActionPermissionSet actionPermissions = resourcePermissions.getPermissions(action);
                //iterate over all super Roles for the current role
                for (RoleWrapper superRole : superRoles ) {
                    Collection<PermissionValue> superRolePermissions =
                        permissions.getResourcePermissionsSet(superRole).getPermissions(action).getPermissions(policy);
//					if ( aLog.isDebugEnabled() ) {
//						aLog.debug("      superRole: " + superRole.getName() + " with " + superRolePermissions.size() + " permissions");
//					}
                    aLog.debug("for superrole " + superRole.getName() + " found " + superRolePermissions.size() + " permissions");
                    //iterate over all permissions, the superrole has and assign as inherited to current role
                    for (PermissionValue permission : superRolePermissions) {
                        permission = PermissionValue.createInheritedRole(permission);
                        //use permission of super role to create an inherited permission for current role
                        actionPermissions.addPermission(policy, permission, CHANGES.INHERITED);
                    }
                }
            }
        }
        aLog.debug("collectInheritedPermissions END");
    }

    /**
     * collects all permissions, which are INDIRECT IMPLICIT
     * @param resource
     * @param permissions_last contains the elements which have changed in the last round, i.e., where it is required to look at a second time
     * @param permissions_dst contains ALL the permissions which have been calculated so far (i.e., which are the end result and are required to come to a decision if something is permitted or not)
     * @param permissions_next in this, the information are stored, which element have to be treated in the next round
     */
    private void collectImplicitPermissions(PolicyWrapper policy, ResourceWrapper resource, PermissionSet permissions_last,
                                            PermissionSet permissions_dst, PermissionSet permissions_next) {
        aLog.debug("collectImplicitPermissions INDIRECT START");
        for (RoleWrapper role : permissions_last.getAllRoleWrappers()) {
            aLog.debug("  role: " + role.getName());
            ResourcePermissionsSet resourcePermissions = permissions_last.getResourcePermissionsSet(role);

            for (Object action : resourcePermissions.getActions()) {
                ActionWrapper actionWrapper = ActionWrapper.createActionWrapper(action);
                aLog.debug("    action: " + actionWrapper.getName());
                ActionPermissionSet actionPermissions = permissions_dst.getResourcePermissionsSet(role).getPermissions(actionWrapper);

                for ( ActionWrapper superAction : getSuperActionWrappersDeep(actionWrapper) ) {

                    Collection<PermissionValue> superActionPermissions = permissions_dst.getResourcePermissionsSet(role).getPermissions(superAction).getPermissions(policy);
//					if ( aLog.isDebugEnabled() ) {
//						aLog.debug("      superAction: " + superAction.getName() + " with " + superActionPermissions.size() + " permissions");
//					}

                    for (PermissionValue permission : superActionPermissions) {
                        //permission = PermissionValue.create(PermissionValue.IMPLICIT, permission.getPermissionWrapper()); //TODO add info about etc
                        permission = PermissionValue.createImplicite(permission);

                        actionPermissions.getPolicyPermissionSet(policy).addPermission(permission, permissions_next, CHANGES.IMPLICIT_SUPER, superAction, role);
                    }
                }

                //implicit permissions from SUB actions
                if ( actionWrapper.hasSubActions() && isPermittedImplicitBySubactions ( permissions_dst, role, actionWrapper, policy ) ) {
                    aLog.debug("IMPLICIT BY SUBACTION: " + role.getName() + " on " + actionWrapper.getName());

                    PermissionValue composite = PermissionValue.createComposite(null, actionWrapper, role);
                    //TODO is there a problem caused throw actionWrapper received from permissions_last?

                    actionPermissions.getPolicyPermissionSet(policy).addPermission(composite, permissions_next, CHANGES.IMPLICIT_SUB, actionWrapper, role);
                }
            }
        }
        aLog.debug("collectImplicitPermissions INDIRECT END");
    }

    /**
     * collects all permissions, which are INDIRECT INHERITED
     * @param resource
     * @param permissions_last
     * @param permissions_dst
     * @param permissions_next
     */
    private void collectInheritedPermissions(PolicyWrapper policy, ResourceWrapper resource, PermissionSet permissions_last,
            PermissionSet permissions_dst, PermissionSet permissions_next) {
        aLog.debug("collectInheritedPermissions INDIRECT START");
        for (RoleWrapper role : permissions_last.getAllRoleWrappers()) {
            ResourcePermissionsSet resourcePermissions = permissions_last.getResourcePermissionsSet(role);
            aLog.debug("  role: " + role.getName());

            for (Object action : resourcePermissions.getActions()) {
                ActionWrapper actionWrapper = ActionWrapper.createActionWrapper(action);
                aLog.debug("    action: " + actionWrapper.getName());
                ActionPermissionSet actionPermissions = permissions_dst.getResourcePermissionsSet(role).getPermissions(actionWrapper);

                for (RoleWrapper superRole :  getSuperRoleWrappersDeep(role)) {
                    Collection<PermissionValue> superRolePermissions = permissions_dst.getResourcePermissionsSet(superRole).getPermissions(action).getPermissions(policy);
                    if ( aLog.isDebugEnabled() ) {
                        aLog.debug("      superRole: " + superRole.getName() + " with " + superRolePermissions.size() + " permissions");
                    }

                    for (PermissionValue permission : superRolePermissions) {
                        permission = PermissionValue.createInheritedRole(permission);
                        //use permission of super role to create an inherited permission for current role
                        actionPermissions.getPolicyPermissionSet(policy).addPermission(permission, permissions_next, CHANGES.INHERITED, actionWrapper, role);
                    }
                }
            }
        }
        aLog.debug("collectInheritedPermissions INDIRECT END");
    }


//	public PermissionSet getExplicitPermission(ResourceWrapper resource, PolicyWrapper policy) {
//		return null;
//	}




    public PermissionSet getExplicitPermissions(ResourceWrapper resource)
    {
        PermissionSet result = new PermissionSet();

        PolicyWrapper defaultPolicy =  HierarchicalPolicyExplorer.getInstance().getDefaultPolicyWrapper();

        for (ActionWrapper action : resource.getActionWrapper()) {
            for ( PermissionWrapper permission : action.getPermissionWrappers()) {
                RoleWrapper role = permission.getRoleWrapper();

                if(role != null) {

                    PolicyWrapper policy = null;

                    Set<PolicyWrapper> policies = permission.getPolicyWrappers();

                    if (policies != null || policies.size() > 0) {
                        policy = policies.iterator().next();

                        if (policies.size() > 1 ) {
                            aLog.error("ignoring all policies except first one.. TODO");
                        }

                    }



                    if ( policy == null ) {
                        policy = defaultPolicy;
                    }

                    result.getResourcePermissionsSet(role).addPermission(action, PermissionValue.createGranted(permission), policy);

                    try {
                        aLog.debug("H: add explicit permission: " + role.getName() + " on "  + action.getName() + " on policy " + (policy == null ? "NULL" : policy.getName() + "_" + policy.getModelElement()));
                    } catch (Exception e) {
                        aLog.debug("error at creating log mesasge: " + e.getClass() + "; " + e.getMessage());
                    }


                    ResourcePermissionsSet rps = result.getResourcePermissionsSet(
                                                     new RoleWrapper(role.getModelElement()));

                    ActionPermissionSet aps = rps.getPermissions(action.getName());

                    aps.setExplicitRoleWrapper(role);
                }
            }
        }
        return result;
    }

    /** gets all direct and indirect superroles
     *
     * @param roleWrapper
     * @return the set of superroles
     */
    public Set<RoleWrapper> getSuperRoleWrappersDeep(RoleWrapper roleWrapper) {
        Set<RoleWrapper> result = new LinkedHashSet<RoleWrapper>();

        collectSuperroles(roleWrapper, result);

        aLog.debug("for role " + roleWrapper.getName() + " found " + result.size() + " superroles");
        return result;
    }

    private static void collectSuperroles(RoleWrapper role,  Set<RoleWrapper> roles) {
        if(roles != null && role.getSuperroles() != null) {
            for ( RoleWrapper superRole  : role.getSuperrolesWrappers() ) {
                roles.add(superRole);

                // recursion
                collectSuperroles(superRole, roles);
            }
        }
    }

    public static Set<RoleWrapper> getSubRoleWrapperDeep(RoleWrapper roleWrapper) {
        Set<RoleWrapper> result = new LinkedHashSet<RoleWrapper>();

        collectSubRoles(roleWrapper, result);

        return result;
    }

    private static void collectSubRoles(RoleWrapper role, Set<RoleWrapper> roles) {
        if (roles != null && role.getSubroles() != null ) {
            for ( RoleWrapper subRole : role.getSubrolesWrappers() ) {
                roles.add(subRole);

                collectSubRoles(subRole, roles);
            }
        }

    }

    /** gets all direct and indirect superactions
     *
     * @param action
     * @return the set of superactions.
     */
    public static Set<ActionWrapper> getSuperActionWrappersDeep(ActionWrapper action) {
        Set<ActionWrapper> result = new LinkedHashSet<ActionWrapper>();

        collectSuperActions(action, result);

        return result;
    }

    public static Set<ActionWrapper> getSubActionWrappersDeep(ActionWrapper action) {
        Set<ActionWrapper> result = new LinkedHashSet<ActionWrapper>();

        collectSubActions(action, result);

        return result;

    }

    public static Set<ActionWrapper> getSubAndSuperActionWrappersDeep(ActionWrapper action) {

        Set<ActionWrapper> result = getSuperActionWrappersDeep(action);
        collectSubActions(action, result);

        return result;
    }

    private static void collectSuperActions(ActionWrapper action, Set<ActionWrapper> result) {
        if(action.getSuperActions() != null) {
            for ( ActionWrapper superAction : action.getSuperActionWrappers()) {
                result.add(superAction);

                collectSuperActions(superAction, result);
            }
        }
    }

    private static void collectSubActions(ActionWrapper action, Set<ActionWrapper> result) {
        if ( action .getSubActions() != null ) {
            for ( ActionWrapper superAction : action.getSubActionWrappers()) {
                result.add(superAction);

                collectSubActions(superAction, result);
            }
        }
    }


    private boolean isPermittedImplicitBySubactions(PermissionSet permissions, RoleWrapper role, ActionWrapper action, PolicyWrapper policy) {
        //check if explicit permitted
        if ( permissions.getResourcePermissionsSet(role).getPermissions(action).getPolicyPermissionSet(policy).isPermitted()) {
            return true;
        }
        else { //check, if implicit permitted by all permitted subactions
            Set<ActionWrapper> subActions = action.getSubActionWrappers();
            if ( subActions != null && subActions.size() > 0 ) {
                for (ActionWrapper subAction : action.getSubActionWrappers()) {
                    if ( ! isPermittedImplicitBySubactions(permissions, role, subAction, policy) ) {
                        return false;
                    }
                }
                return true;
            }
            else {
                return false;
            }
        }
    }
}
