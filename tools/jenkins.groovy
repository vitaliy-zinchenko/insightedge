def String getBranchOrDefault(String repo, String targetBranch, String defaultBranch) {
    // default branch is preferred over master
    if (targetBranch.equals("master")) {
        return defaultBranch
    }

    // write a number of branches matching target to a file
    // never fails with non-zero status code (using ||: syntax)
    sh "git ls-remote --heads $repo | grep -c $targetBranch > temp-branch-count || :"
    String branchMatchCount = readFile("temp-branch-count").trim()
    if (branchMatchCount.equals("1")) {
        echo "Branch $targetBranch found at: $repo"
        return targetBranch
    } else {
        echo "Found $branchMatchCount branches matching $targetBranch at: $repo; Falling to default branch: $defaultBranch"
        return defaultBranch
    }
}


String branchName = "$env.BRANCH_NAME"

String zeppelinRepo = "https://\$USERNAME:\$PASSWORD@github.com/InsightEdge/insightedge-zeppelin.git"
String zeppelinDefaultBranchName = "branch-0.5.6"

String examplesRepo = "https://\$USERNAME:\$PASSWORD@github.com/InsightEdge/insightedge-examples.git"
String examplesDefaultBranchName = "master"

echo "Branch: $branchName"
sh 'git log -1 --format="%H" > temp-git-commit-hash'
String commitHash = readFile("temp-git-commit-hash").trim()
echo "Commit: $commitHash"

env.PATH = "${tool 'maven-3.3.9'}/bin:$env.PATH"
env.PATH = "${tool 'sbt-0.13.11'}/bin:$env.PATH"

withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'insightedge-dev', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {

    stage 'Build insightedge'
    load 'tools/build.groovy'


    stage 'Checkout zeppelin'
    String zeppelinBranchName = getBranchOrDefault(zeppelinRepo, branchName, zeppelinDefaultBranchName)
    echo "Using $zeppelinBranchName branch for Zeppelin"
    sh "rm -r zeppelin || :"
    sh "git clone -b $zeppelinBranchName --single-branch $zeppelinRepo zeppelin/$zeppelinBranchName"


    stage 'Build zeppelin'
    dir("zeppelin/$zeppelinBranchName") {
        load 'tools/build.groovy'
    }


    stage 'Checkout examples'
    String examplesBranchName = getBranchOrDefault(examplesRepo, branchName, examplesDefaultBranchName)
    echo "Using $examplesBranchName branch for examples"
    sh "rm -r examples || :"
    sh "git clone -b $examplesBranchName --single-branch $examplesRepo examples/$examplesBranchName"


    stage 'Build examples'
    dir("examples/$examplesBranchName") {
        load 'tools/build.groovy'
    }


    stage 'Package insightedge'
    distributions = "-Ddist.spark=$env.SPARK_DIST"
    distributions = "$distributions -Ddist.xap=$env.XAP_DIST"
    distributions = "$distributions -Ddist.zeppelin=zeppelin/$zeppelinBranchName/zeppelin-distribution/target/zeppelin-0.5.7-incubating-SNAPSHOT.tar.gz"
    distributions = "$distributions -Ddist.examples=examples/$examplesBranchName/target/insightedge-examples.jar"
    sh "mvn package -pl insightedge-packager -DskipTests=true -P package-deployment $distributions -Dlast.commit.hash=$commitHash"


    stage 'Export artifacts'
    archive 'insightedge-packager/target/gigaspaces-*.zip'
}