#! /bin/bash 

function getProperty {
    PROP_KEY=$1
    PROP_VALUE=`cat gradle.properties | grep "$PROP_KEY" | cut -d'=' -f2`
    echo $PROP_VALUE
}

basedir=$(getProperty "repoParent")
workdir=$(cd $(dirname $0); pwd)
proj_id=$(gradle properties -q | grep "name:" | awk '{print $2}' | tr -d '[:space:]')
proj_version=$(gradle properties -q | grep "version:" | awk '{print $2}' | tr -d '[:space:]')
repo_path=$basedir'Akarin-repo/'

(
if [ ! -d $repo_path ]; then
 echo '[Deploy] Synchronizing remote..'
 cd $basedir
 git clone 'https://github.com/Akarin-project/Akarin-repo.git'
 echo '[Deploy] Synchronized remote!'
 cd $workdir
fi

echo '[Deploy] Deploy to local repository..'
gradle publish
echo '[Deploy] Deployed to local repository successfully'

cd $repo_path
echo '[Deploy] Synchronizing remote..'

commit="git commit -m '[Deploy] Update "$proj_id" "$proj_version"'"
git pull && git add . && eval $commit && git push

echo '[Deploy] Deployed to Akarin-repo successfully!'
)