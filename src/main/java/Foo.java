import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabGroup;

/**
 * @author Kohsuke Kawaguchi
 */
public class Foo {
    public static void main(String[] args) throws Exception {
        GitlabAPI con = GitlabAPI.connect("http://gitlab.example.com:8080/", "J3_wGdxJqr964Bowozwo");
        GitlabGroup v = con.getGroup("wombat-inc");
        System.out.println(v);
        System.out.println(v.getName());
    }
}
