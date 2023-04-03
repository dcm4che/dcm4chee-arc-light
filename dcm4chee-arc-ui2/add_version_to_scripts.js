const fs = require('fs');
console.log("Reading pom.xml...");
fs.readFile('pom.xml', 'utf8', (err, pomContent) => {
    console.log("Extracting version from pom.xml...");
    if (err) {
        console.error(err);
        return;
    }
    const regex = /<version>(\d+.\d+.\d+)<\/version>/;
    let m;

    if ((m = regex.exec(pomContent)) !== null) {
        // The result can be accessed through the `m`-variable.

        if(m && m[1]){
            console.log("Found version=",m[1]);
            let version = m[1];
            console.log("Reading index.html from target/webapp...");
            fs.readFile('target/webapp/index.html','utf8',(err, indexContent)=>{
                console.log("Adding version to script tags...");
                const replaceRegex = /(<script[ \w_"'-]*src="[\w._-]+)(\.js)(")/g;
                const subst = `$1$2?${version}$3`;
                const newHtmlContent = indexContent.replace(replaceRegex, subst);
                console.log("Writing changed HTML-Content back to target/webapp/index.html...");
                fs.writeFile('target/webapp/index.html', newHtmlContent, err => {
                    if (err) {
                        console.error(err);
                    }
                    console.log("HTML-Content updated successfully!");
                });
            });
        }
    }
});
