package ch.ethz.infsec.secureumlgui.securemodel.secureuml;

/**
 * User class proxy interface.
 *
 * <p><em><strong>Note:</strong> This type should not be subclassed or implemented
 * by clients. It is generated from a MOF metamodel and automatically implemented
 * by MDR (see <a href="http://mdr.netbeans.org/">mdr.netbeans.org</a>).</em></p>
 */
public interface UserClass extends javax.jmi.reflect.RefClass {
    /**
     * The default factory operation used to create an instance object.
     * @return The created instance object.
     */
    public User createUser();
    /**
     * Creates an instance object having attributes initialized by the passed
     * values.
     * @param name
     * @return The created instance object.
     */
    public User createUser(java.lang.String name);
}
