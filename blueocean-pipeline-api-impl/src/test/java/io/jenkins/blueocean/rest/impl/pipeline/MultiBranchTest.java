package io.jenkins.blueocean.rest.impl.pipeline;

import com.google.common.collect.ImmutableMap;
import hudson.Util;
import hudson.model.FreeStyleProject;
import hudson.plugins.favorite.user.FavoriteUserProperty;
import hudson.plugins.git.util.BuildData;
import hudson.scm.ChangeLogSet;
import io.jenkins.blueocean.rest.hal.LinkResolver;
import io.jenkins.blueocean.rest.impl.pipeline.scm.GitSampleRepoRule;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.SCMSource;
import org.apache.commons.lang.StringUtils;
import org.hamcrest.collection.IsArrayContainingInAnyOrder;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.MockFolder;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static io.jenkins.blueocean.rest.model.BlueRun.DATE_FORMAT_STRING;
import static org.junit.Assert.*;

/**
 * @author Vivek Pandey
 */
public class MultiBranchTest extends PipelineBaseTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();


    private final String[] branches={"master", "feature%2Fux-1", "feature2"};

    @Before
    public void setup() throws Exception{
        super.setup();
        setupScm();
    }

    /**
     * Some of these tests can be problematic until:
     * https://issues.jenkins-ci.org/browse/JENKINS-36290 is resolved
     * Set an env var to any value to get these to run.
     */
    private boolean runAllTests() {
        return System.getenv("RUN_MULTIBRANCH_TESTS") != null;
    }


    @Test
    public void resolveMbpLink() throws Exception {
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        FreeStyleProject f = j.jenkins.createProject(FreeStyleProject.class, "f");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false),
            new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }

        mp.scheduleBuild2(0).getFuture().get();

        j.waitUntilNoActivity();

        Assert.assertEquals("/blue/rest/organizations/jenkins/pipelines/p/",LinkResolver.resolveLink(mp).getHref());
        Assert.assertEquals("/blue/rest/organizations/jenkins/pipelines/p/branches/master/",LinkResolver.resolveLink(mp.getBranch("master")).getHref());
        Assert.assertEquals("/blue/rest/organizations/jenkins/pipelines/p/branches/feature%252Fux-1/",LinkResolver.resolveLink(mp.getBranch("feature%2Fux-1")).getHref());
        Assert.assertEquals("/blue/rest/organizations/jenkins/pipelines/p/branches/feature2/",LinkResolver.resolveLink(mp.getBranch("feature2")).getHref());
        Assert.assertEquals("/blue/rest/organizations/jenkins/pipelines/f/",LinkResolver.resolveLink(f).getHref());
    }


    @Test
    public void getMultiBranchPipelines() throws IOException, ExecutionException, InterruptedException {
        Assume.assumeTrue(runAllTests());
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        FreeStyleProject f = j.jenkins.createProject(FreeStyleProject.class, "f");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false),
            new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }

        mp.scheduleBuild2(0).getFuture().get();

        List<Map> resp = get("/organizations/jenkins/pipelines/", List.class);
        Assert.assertEquals(2, resp.size());
        validatePipeline(f, resp.get(0));
        validateMultiBranchPipeline(mp, resp.get(1), 3);
        Assert.assertEquals(mp.getBranch("master").getBuildHealth().getScore(), resp.get(0).get("weatherScore"));
    }


    @Test
    public void getMultiBranchPipelineInsideFolder() throws IOException, ExecutionException, InterruptedException {
        MockFolder folder1 = j.createFolder("folder1");
        WorkflowMultiBranchProject mp = folder1.createProject(WorkflowMultiBranchProject.class, "p");

        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false),
            new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }

        mp.scheduleBuild2(0).getFuture().get();

        Map r = get("/organizations/jenkins/pipelines/folder1/pipelines/p/");

        validateMultiBranchPipeline(mp, r, 3);
        Assert.assertEquals("/blue/rest/organizations/jenkins/pipelines/folder1/pipelines/p/",
            ((Map)((Map)r.get("_links")).get("self")).get("href"));
    }

    @Test
    public void getBranchWithEncodedPath() throws IOException, ExecutionException, InterruptedException {
        Assume.assumeTrue(runAllTests());
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        FreeStyleProject f = j.jenkins.createProject(FreeStyleProject.class, "f");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false),
            new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }

        mp.scheduleBuild2(0).getFuture().get();

        List<Map> resp = get("/organizations/jenkins/pipelines/p/branches/", List.class);

        String href = null;
        for(Map r: resp){
            if(r.get("name").equals("feature%2Fux-1")){
                href = (String) ((Map)((Map)r.get("_links")).get("self")).get("href");

                href = StringUtils.substringAfter(href,"/blue/rest");
            }
        }
        Assert.assertNotNull(href);
        Map r = get(href);
        Assert.assertEquals("feature%2Fux-1", r.get("name"));
    }

    @Test
    public void getMultiBranchPipelinesWithNonMasterBranch() throws Exception {
        sampleRepo.git("branch","-D", "master");
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");

        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false),
            new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }

        mp.scheduleBuild2(0).getFuture().get();

        List<Map> resp = get("/organizations/jenkins/pipelines/", List.class);
        Assert.assertEquals(1, resp.size());
        validateMultiBranchPipeline(mp, resp.get(0), 2);
        Assert.assertNull(mp.getBranch("master"));
    }

    @Test
    public void getMultiBranchPipeline() throws IOException, ExecutionException, InterruptedException {
        Assume.assumeTrue(runAllTests());
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false),
            new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }

        mp.scheduleBuild2(0).getFuture().get();


        Map resp = get("/organizations/jenkins/pipelines/p/");
        validateMultiBranchPipeline(mp, resp, 3);

        List<String> names = (List<String>) resp.get("branchNames");

        IsArrayContainingInAnyOrder.arrayContainingInAnyOrder(names, branches);

        List<Map> br = get("/organizations/jenkins/pipelines/p/branches", List.class);

        List<String> branchNames = new ArrayList<>();
        List<Integer> weather = new ArrayList<>();
        for (Map b : br) {
            branchNames.add((String) b.get("name"));
            weather.add((int) b.get("weatherScore"));
        }

        for (String n : branches) {
            assertTrue(branchNames.contains(n));
        }

        for (int s : weather) {
            assertEquals(100, s);
        }
    }

    @Test
    public void getMultiBranchPipelineRuns() throws Exception {
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false),
            new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }

        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        j.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        assertEquals(3, mp.getItems().size());

        //execute feature/ux-1 branch build
        p = scheduleAndFindBranchProject(mp, "feature%2Fux-1");
        j.waitUntilNoActivity();
        WorkflowRun b2 = p.getLastBuild();
        assertEquals(1, b2.getNumber());


        //execute feature 2 branch build
        p = scheduleAndFindBranchProject(mp, "feature2");
        j.waitUntilNoActivity();
        WorkflowRun b3 = p.getLastBuild();
        assertEquals(1, b3.getNumber());


        List<Map> br = get("/organizations/jenkins/pipelines/p/branches", List.class);

        List<String> branchNames = new ArrayList<>();
        List<Integer> weather = new ArrayList<>();
        for(Map b: br){
            branchNames.add((String) b.get("name"));
            weather.add((int) b.get("weatherScore"));
        }
        Assert.assertTrue(branchNames.contains(((Map)(br.get(0).get("latestRun"))).get("pipeline")));

        for(String n:branches){
            assertTrue(branchNames.contains(n));
        }

        WorkflowRun[] runs = {b1,b2,b3};

        int i = 0;
        for(String n:branches){
            WorkflowRun b = runs[i];
            j.waitForCompletion(b);
            Map run = get("/organizations/jenkins/pipelines/p/branches/"+ Util.rawEncode(n)+"/runs/"+b.getId());
            validateRun(b,run);
            i++;
        }

        Map pr = get("/organizations/jenkins/pipelines/p/");
        validateMultiBranchPipeline(mp, pr, 3,3,0);

        sampleRepo.git("checkout","master");
        sampleRepo.write("file", "subsequent content11");
        sampleRepo.git("commit", "--all", "--message=tweaked11");

        p = scheduleAndFindBranchProject(mp, "master");
        j.waitUntilNoActivity();
        WorkflowRun b4 = p.getLastBuild();
        assertEquals(2, b4.getNumber());

        List<Map> run = get("/organizations/jenkins/pipelines/p/branches/master/runs/", List.class);
        validateRun(b4, run.get(0));
    }

    @Test
    public void getMultiBranchPipelineActivityRuns() throws Exception {
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false),
            new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }
        scheduleAndFindBranchProject(mp);
        j.waitUntilNoActivity();

        WorkflowJob p = findBranchProject(mp, "master");

        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        assertEquals(3, mp.getItems().size());

        //execute feature/ux-1 branch build
        p = findBranchProject(mp, "feature%2Fux-1");
        WorkflowRun b2 = p.getLastBuild();
        assertEquals(1, b2.getNumber());


        //execute feature 2 branch build
        p = findBranchProject(mp, "feature2");
        WorkflowRun b3 = p.getLastBuild();
        assertEquals(1, b3.getNumber());


        List<Map> resp = get("/organizations/jenkins/pipelines/p/runs", List.class);
        Assert.assertEquals(3, resp.size());
        Date d1 = new SimpleDateFormat(DATE_FORMAT_STRING).parse((String)resp.get(0).get("startTime"));
        Date d2 = new SimpleDateFormat(DATE_FORMAT_STRING).parse((String)resp.get(1).get("startTime"));
        Date d3 = new SimpleDateFormat(DATE_FORMAT_STRING).parse((String)resp.get(2).get("startTime"));

        Assert.assertTrue(d1.compareTo(d2) >= 0);
        Assert.assertTrue(d2.compareTo(d3) >= 0);

        for(Map m: resp){
            BuildData d;
            WorkflowRun r;
            if(m.get("pipeline").equals("master")){
                r = b1;
                d = b1.getAction(BuildData.class);
            } else if(m.get("pipeline").equals("feature2")){
                r = b3;
                d = b3.getAction(BuildData.class);
            } else{
                r = b2;
                d = b2.getAction(BuildData.class);
            }
            validateRun(r,m);
            String commitId = "";
            if(d != null) {
                commitId = d.getLastBuiltRevision().getSha1String();
                Assert.assertEquals(commitId, m.get("commitId"));
            }
        }
    }

    @Test
    public void getMultiBranchPipelineRunChangeSets() throws Exception {
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false),
            new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }

        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        j.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        assertEquals(3, mp.getItems().size());


        String[] messages = {"tweaked11","tweaked12","tweaked13","tweaked14"};

        sampleRepo.git("checkout","master");
        sampleRepo.write("file", "subsequent content11");
        sampleRepo.git("commit", "--all", "--message="+messages[0]);

        sampleRepo.git("checkout","master");
        sampleRepo.write("file", "subsequent content12");
        sampleRepo.git("commit", "--all", "--message="+messages[1]);

        sampleRepo.git("checkout","master");
        sampleRepo.write("file", "subsequent content13");
        sampleRepo.git("commit", "--all", "--message="+messages[2]);


        sampleRepo.git("checkout","master");
        sampleRepo.write("file", "subsequent content14");
        sampleRepo.git("commit", "--all", "--message="+messages[3]);


        p = scheduleAndFindBranchProject(mp, "master");
        j.waitUntilNoActivity();
        WorkflowRun b4 = p.getLastBuild();
        assertEquals(2, b4.getNumber());

        ChangeLogSet.Entry changeLog = b4.getChangeSets().get(0).iterator().next();

        int i=0;
        for(ChangeLogSet.Entry c:b4.getChangeSets().get(0)){
            Assert.assertEquals(messages[i], c.getMsg());
            i++;
        }

        Map run = get("/organizations/jenkins/pipelines/p/branches/master/runs/"+b4.getId()+"/");
        validateRun(b4, run);
        List<Map> changetSet = (List<Map>) run.get("changeSet");

        Map c = changetSet.get(0);

        Assert.assertEquals(changeLog.getCommitId(), c.get("commitId"));
        Map a = (Map) c.get("author");
        Assert.assertEquals(changeLog.getAuthor().getId(), a.get("id"));
        Assert.assertEquals(changeLog.getAuthor().getFullName(), a.get("fullName"));

        int j=0;
        for(ChangeLogSet.Entry cs:b4.getChangeSets().get(0)){
            Assert.assertEquals(cs.getCommitId(),changetSet.get(j).get("commitId"));
            j++;
        }
    }

    @Test
    public void createUserFavouriteMultibranchTopLevelTest() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        hudson.model.User user = j.jenkins.getUser("alice");
        user.setFullName("Alice Cooper");
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false),
            new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }

        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        j.waitUntilNoActivity();

        Map m = new RequestBuilder(baseUrl)
            .put("/organizations/jenkins/pipelines/p/favorite")
            .auth("alice", "alice")
            .data(ImmutableMap.of("favorite", true))
            .build(Map.class);

        Map branch = (Map) m.get("item");
        validatePipeline(p, branch);
        String c = (String) branch.get("_class");
        Assert.assertEquals(BranchImpl.class.getName(), c);


        List l = new RequestBuilder(baseUrl)
            .get("/users/"+user.getId()+"/favorites/")
            .auth("alice","alice")
            .build(List.class);

        Assert.assertEquals(l.size(), 1);
        branch = (Map)((Map)l.get(0)).get("item");

        validatePipeline(p, branch);

        c = (String) branch.get("_class");
        Assert.assertEquals(BranchImpl.class.getName(), c);

        new RequestBuilder(baseUrl)
            .get("/users/"+user.getId()+"/favorites/")
            .auth("bob","bob")
            .status(403)
            .build(String.class);
    }


    @Test
    public void createUserFavouriteMultibranchBranchTest() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        hudson.model.User user = j.jenkins.getUser("alice");
        user.setFullName("Alice Cooper");
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false),
            new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }

        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        j.waitUntilNoActivity();

        WorkflowJob p1 = scheduleAndFindBranchProject(mp, "feature2");

        Map map = new RequestBuilder(baseUrl)
            .put("/organizations/jenkins/pipelines/p/branches/feature2/favorite")
            .auth("alice", "alice")
            .data(ImmutableMap.of("favorite", true))
            .build(Map.class);


        validatePipeline(p1, (Map) map.get("item"));

        List l = new RequestBuilder(baseUrl)
            .get("/users/"+user.getId()+"/favorites/")
            .auth("alice","alice")
            .build(List.class);

        Assert.assertEquals(l.size(), 1);

        Map branch = (Map)((Map)l.get(0)).get("item");

        validatePipeline(p1, branch);

        String c = (String) branch.get("_class");
        Assert.assertEquals(BranchImpl.class.getName(), c);

        new RequestBuilder(baseUrl)
            .get("/users/"+user.getId()+"/favorites/")
            .auth("bob","bob")
            .status(403)
            .build(String.class);

    }


    @Test
    public void favoritedFromClassicTest() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        hudson.model.User user = j.jenkins.getUser("alice");
        user.setFullName("Alice Cooper");
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false),
            new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }

        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        j.waitUntilNoActivity();

        FavoriteUserProperty fup = user.getProperty(FavoriteUserProperty.class);
        if (fup == null) {
            user.addProperty(new FavoriteUserProperty());
            fup = user.getProperty(FavoriteUserProperty.class);
        }
        fup.toggleFavorite(mp.getFullName());
        user.save();

        List l = new RequestBuilder(baseUrl)
            .get("/users/"+user.getId()+"/favorites/")
            .auth("alice","alice")
            .build(List.class);

        Assert.assertEquals(1, l.size());

        Map branch = (Map)((Map)l.get(0)).get("item");

        validatePipeline(p, branch);

        String c = (String) branch.get("_class");
        Assert.assertEquals(BranchImpl.class.getName(), c);

        String ref = getHrefFromLinks((Map)l.get(0), "self");

        Map r = new RequestBuilder(baseUrl)
            .get(ref.substring("/blue/rest".length()))
            .auth("alice","alice")
            .build(Map.class);

        validatePipeline(p, (Map)r.get("item"));

    }

    @Test
    public void getMultiBranchPipelineRunStages() throws Exception {
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false),
            new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }

        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");


        j.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        assertEquals(3, mp.getItems().size());

        j.waitForCompletion(b1);

        List<Map> nodes = get("/organizations/jenkins/pipelines/p/branches/master/runs/1/nodes", List.class);

        Assert.assertEquals(3, nodes.size());
    }


    @Test
    public void getPipelinesTest() throws Exception {

        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false),
            new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }

        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");


        j.waitUntilNoActivity();

        List<Map> responses = get("/search/?q=type:pipeline;excludedFromFlattening:jenkins.branch.MultiBranchProject", List.class);
        Assert.assertEquals(1, responses.size());
    }


    private void setupScm() throws Exception {
        // create git repo
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "stage 'build'\n "+"node {echo 'Building'}\n"+
            "stage 'test'\nnode { echo 'Testing'}\n"+
            "stage 'deploy'\nnode { echo 'Deploying'}\n"
        );
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");

        //create feature branch
        sampleRepo.git("checkout", "-b", "feature/ux-1");
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; "+"node {" +
            "   stage ('Build'); " +
            "   echo ('Building'); " +
            "   stage ('Test'); " +
            "   echo ('Testing'); " +
            "   stage ('Deploy'); " +
            "   echo ('Deploying'); " +
            "}");
        ScriptApproval.get().approveSignature("method java.lang.String toUpperCase");
        sampleRepo.write("file", "subsequent content1");
        sampleRepo.git("commit", "--all", "--message=tweaked1");

        //create feature branch
        sampleRepo.git("checkout", "-b", "feature2");
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; "+"node {" +
            "   stage ('Build'); " +
            "   echo ('Building'); " +
            "   stage ('Test'); " +
            "   echo ('Testing'); " +
            "   stage ('Deploy'); " +
            "   echo ('Deploying'); " +
            "}");
        ScriptApproval.get().approveSignature("method java.lang.String toUpperCase");
        sampleRepo.write("file", "subsequent content2");
        sampleRepo.git("commit", "--all", "--message=tweaked2");
    }

    private WorkflowJob scheduleAndFindBranchProject(WorkflowMultiBranchProject mp,  String name) throws Exception {
        mp.scheduleBuild2(0).getFuture().get();
        return findBranchProject(mp, name);
    }

    private void scheduleAndFindBranchProject(WorkflowMultiBranchProject mp) throws Exception {
        mp.scheduleBuild2(0).getFuture().get();
    }

    private WorkflowJob findBranchProject(WorkflowMultiBranchProject mp,  String name) throws Exception {
        WorkflowJob p = mp.getItem(name);
        if (p == null) {
            mp.getIndexing().writeWholeLogTo(System.out);
            fail(name + " project not found");
        }
        return p;
    }

}
