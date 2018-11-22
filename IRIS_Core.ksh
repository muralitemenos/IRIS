target=$1
RELEASE=$2


case "$target" in

   "MonthlyVersionUpgrade")

			if [ -n "$RELEASE" ] 
			then
				#configuration
				git config user.email "integbuild@temenos.com"
				git config user.name "TemIntegbuild"
				git config remote.origin.url https://github.com/temenostech/IRIS.git

				#branch creation 
				git checkout -b $RELEASE

				#version changes
				relver=$RELEASE".0.0"
				snapver=`grep "<version>0.*</version>" pom.xml | cut -d'<' -f2 | cut -d'>' -f2`
				qualver=`grep "<version>0.*</version>" pom.xml | cut -d'<' -f2 | cut -d'>' -f2 | cut -d'-' -f1`
				qualname="$qualver".qualifier

				for i in `grep -rw "$snapver" | cut -d":" -f1`
				do
				echo "Updating new version $i..."
				sed -i -e "s|$snapver|$relver|g" $i
				grep "$relver" $i
				done

				for i in `grep -rw "$qualname" | cut -d":" -f1`
				do

				echo "Updating new version $i..."
				sed -i -e "s|$qualname|$relver|g" $i
				grep "$relver" $i
				done

				git add --all
				git remote -v
				git reset IRIS_Core.ksh
				git commit -m "IRIS $RELEASE release ($relver)"
				git status
				#git remote set-url origin https://TemIntegbuild:Temenos1010@github.com/temenostech/IRIS.git
				#git push -u origin $RELEASE

			else
				echo "[ERROR]: Inputted <$RELEASE> seems to be wrong...Exiting"
				exit 1
			fi

        ;;

	"R17VersionUpgrade")

			export JAVA_HOME=/opt/jdk1.7.0_17
			export MAVEN_HOME=/opt/apache-maven-3.0.5
			export PATH=/home/utp/jdk7/bin:/opt/apache-maven-3.0.5/bin:/usr/local/bin:/usr/bin:$PATH
			export MAVEN_OPTS="-Xms512m -Xmx4096m -XX:PermSize=256m -XX:MaxPermSize=512m -ea"
			echo $JAVA_HOME
			echo $MAVEN_HOME
			echo $MAVEN_OPTS

			if [ -n "$RELEASE" ] && [ "$GIT_BRANCH" == "0.14.x" ]
			then
				#configuration
				git config user.email "integbuild@temenos.com"
				git config user.name "TemIntegbuild"
				git config remote.origin.url https://github.com/temenostech/IRIS.git
				#branch creation 
				git checkout -b"iris_cut_$RELEASE" --track origin/$GIT_BRANCH

				#git log --oneline origin/0.14.X...origin/master --cherry-pick --no-merges --left-right

				#version changes
				w="";x="";y="";z=""
				w=`grep "<version>0.14.*</version>" pom.xml | cut -d'<' -f2 | cut -d'>' -f2`
				x=`grep "<version>0.14.*</version>" pom.xml | cut -d'<' -f2 | cut -d'>' -f2 | cut -d'.' -f3`
				y=`expr $x + 1`
				z="0.14.$y"

				for i in `grep -rw "$w" | grep -v .git/ | cut -d":" -f1`
				do
				echo "Updating new version $i..."
				sed -i -e "s|$w|$z|g" $i
				grep "$z" $i
				done

				git add --all
				git remote -v
				git commit -m"iris cut $z for $RELEASE"
				git status
                #Perform build
				/opt/apache-maven-3.0.5/bin/mvn clean install -U -s /home/utp/.m2/settings.xml -DaltDeploymentRepository=nexus:t24::default::http://maven.temenosgroup.com/content/repositories/t24-release -DrepositoryId=nexus-t24 -Dmaven.artifacts.threads=2
				
			else
				echo "[ERROR]: Inputted <Release> or <BranchName> seems to be wrong...Exiting"
				exit 1
			fi

			;;
			
	"R17Deploy")
	
			if [ -n "$GIT_BRANCH" ]
			then
				export JAVA_HOME=/opt/jdk1.7.0_17
				export MAVEN_HOME=/opt/apache-maven-3.0.5
				export PATH=/home/utp/jdk7/bin:/opt/apache-maven-3.0.5/bin:/usr/local/bin:/usr/bin:$PATH
				export MAVEN_OPTS="-Xms512m -Xmx4096m -XX:PermSize=256m -XX:MaxPermSize=512m -ea"
				echo $JAVA_HOME
				echo $MAVEN_HOME
				echo $MAVEN_OPTS

				/opt/apache-maven-3.0.5/bin/mvn clean deploy -U -s /home/utp/.m2/settings.xml -DaltDeploymentRepository=nexus-t24::default::http://maven.temenosgroup.com/content/repositories/t24-releases -DrepositoryId=nexus-t24 -Dmaven.artifact.threads=2

			else
				echo "[ERROR]: Inputted <BranchName> seems to be wrong...Exiting"
				exit 1
			fi
	
			;;
	
	"R16VersionUpgrade")
	
			export JAVA_HOME=/opt/jdk1.7.0_17
			export MAVEN_HOME=/opt/apache-maven-3.0.5
			export PATH=/home/utp/jdk7/bin:/opt/apache-maven-3.0.5/bin:/usr/local/bin:/usr/bin:$PATH
			export MAVEN_OPTS="-Xms512m -Xmx4096m -XX:PermSize=256m -XX:MaxPermSize=512m -ea"
			echo $JAVA_HOME
			echo $MAVEN_HOME
			echo $MAVEN_OPTS

			if [ -n "$RELEASE" ] && [ "$GIT_BRANCH" == "0.9.x" ]
			then
				#configuration
				git config user.email "integbuild@temenos.com"
				git config user.name "TemIntegbuild"
				git config remote.origin.url https://github.com/temenostech/IRIS.git
				#branch creation 
				git checkout -b"iris_cut_$RELEASE" --track origin/$GIT_BRANCH

				#git log --oneline origin/0.14.X...origin/master --cherry-pick --no-merges --left-right

				#version changes
				w="";x="";y="";z=""
				w=`grep "<version>0.14.*</version>" pom.xml | cut -d'<' -f2 | cut -d'>' -f2`
				x=`grep "<version>0.14.*</version>" pom.xml | cut -d'<' -f2 | cut -d'>' -f2 | cut -d'.' -f3`
				y=`expr $x + 1`
				z="0.9.$y"

				for i in `grep -rw "$w" | cut -d":" -f1`
				do
				echo "Updating new version $i..."
				sed -i -e "s|$w|$z|g" $i
				grep "$z" $i
				done

				git add --all
				git remote -v
				git commit -m"iris cut $z for $RELEASE"
				git status

				/opt/apache-maven-3.0.5/bin/mvn clean install -U -s /home/utp/.m2/settings.xml -DaltDeploymentRepository=nexus:t24::default::http://maven.temenosgroup.com/content/repositories/t24-release -DrepositoryId=nexus-t24 -Dmaven.artifacts.threads=2

			else
				echo "[ERROR]: Inputted <Release> or <BranchName> seems to be wrong...Exiting"
				exit 1
			fi

			;;
			
	"R16Deploy")
	
			if [ -n "$GIT_BRANCH" ]
			then
			export JAVA_HOME=/opt/jdk1.7.0_17
			export MAVEN_HOME=/opt/apache-maven-3.0.5
			export PATH=/home/utp/jdk7/bin:/opt/apache-maven-3.0.5/bin:/usr/local/bin:/usr/bin:$PATH
			export MAVEN_OPTS="-Xms512m -Xmx4096m -XX:PermSize=256m -XX:MaxPermSize=512m -ea"
			echo $JAVA_HOME
			echo $MAVEN_HOME
			echo $MAVEN_OPTS

				/opt/apache-maven-3.0.5/bin/mvn -X clean deploy -U -s /home/utp/.m2/settings.xml -DaltDeploymentRepository=nexus-t24::default::http://maven.temenosgroup.com/content/repositories/t24-releases -DrepositoryId=nexus-t24 -Dmaven.artifact.threads=2

			else
				echo "[ERROR]: Inputted <BranchName> seems to be wrong...Exiting"
				exit 1
			fi
	
			;;
			
	"201702VersionUpgrade")
	
			if [ -n "$RELEASE" ] && [ "$GIT_BRANCH" == "0.13.x" ]
			then
				#configuration
				git config user.email "integbuild@temenos.com"
				git config user.name "TemIntegbuild"
				git config remote.origin.url https://github.com/temenostech/IRIS.git
				#branch creation 
				git checkout -b"iris_cut_$RELEASE" --track origin/$GIT_BRANCH

				#git log --oneline origin/0.13.X...origin/master --cherry-pick --no-merges --left-right

				#version chnages
				w="";x="";y="";z=""
				w=`grep "<version>0.13.*</version>" pom.xml | cut -d'<' -f2 | cut -d'>' -f2`
				x=`grep "<version>0.13.*</version>" pom.xml | cut -d'<' -f2 | cut -d'>' -f2 | cut -d'.' -f3`
				y=`expr $x + 1`
				z="0.13.$y"

				for i in `grep -rw "$w" | cut -d":" -f1`
				do
				echo "Updating new version $i..."
				sed -i -e "s|$w|$z|g" $i
				grep "$z" $i
				done

				git add --all
				git remote -v
				git commit -m"iris cut $z for $RELEASE"
				git status
			else
				echo "[ERROR]: Inputted <Release> or <BranchName> seems to be wrong...Exiting"
				exit 1
			fi

			;;
			
	"201702to201805VersionUpgrade")
	
			export JAVA_HOME=/opt/jdk1.8.0_121
				
			if [ -n "$RELEASE" ] && [ "$GIT_BRANCH" == "0.15.X" ]
			then
				#configuration
				git config user.email "integbuild@temenos.com"
				git config user.name "TemIntegbuild"
				git config remote.origin.url https://github.com/temenostech/IRIS.git
				#branch creation 
				git checkout -b"iris_cut_$RELEASE" --track origin/$GIT_BRANCH

				#git log --oneline origin/0.15.X...origin/master --cherry-pick --no-merges --left-right

				#version chnages
				w="";x="";y="";z=""
				w=`grep "<version>0.15.*</version>" pom.xml | cut -d'<' -f2 | cut -d'>' -f2`
				x=`grep "<version>0.15.*</version>" pom.xml | cut -d'<' -f2 | cut -d'>' -f2 | cut -d'.' -f3`
				y=`expr $x + 1`
				z="0.15.$y"

				for i in `grep -rw "$w" | cut -d":" -f1`
				do
				echo "Updating new version $i..."
				sed -i -e "s|$w|$z|g" $i
				grep "$z" $i
				done

				git add --all
				git remote -v
				git commit -m"IRIS $RELEASE release ($z)"
				git status
			else
				echo "[ERROR]: Inputted <Release> or <BranchName> seems to be wrong...Exiting"
				exit 1
			fi

			;;
			
	"201806VersionUpgrade")
	
			export JAVA_HOME=/opt/jdk1.8.0_121
					
			if [ -n "$RELEASE" ]
			then
				#configuration
				git config user.email "integbuild@temenos.com"
				git config user.name "TemIntegbuild"
				git config remote.origin.url https://github.com/temenostech/IRIS.git
				#branch creation 
				git checkout -b"iris_cut_$RELEASE" --track origin/$RELEASE

				#version chnages
				w="";x="";y="";z=""
				w=`grep "<version>$RELEASE.0.*</version>" pom.xml | cut -d'<' -f2 | cut -d'>' -f2`
				x=`grep "<version>$RELEASE.0.*</version>" pom.xml | cut -d'<' -f2 | cut -d'>' -f2 | cut -d'.' -f3`
				y=`expr $x + 1`
				z="$RELEASE.0.$y"

				for i in `grep -rw "$w" | cut -d":" -f1`
				do
				echo "Updating new version $i..."
				sed -i -e "s|$w|$z|g" $i
				grep "$z" $i
				done

				git add --all
				git remote -v
				git commit -m"IRIS $RELEASE release ($z)"
				git status
			else
				echo "[ERROR]: Inputted <Release> seems to be wrong...Exiting"
				exit 1
			fi

			;;

        *)
                echo "ERROR: Target not provided. Kindly check"
                exit 1
        ;;


esac
