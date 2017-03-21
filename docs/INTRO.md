# Getting Started 

First, clone rhsm-qe into a working directory, and for testing purposes, switch to the stoner branch

```
git clone git@github.com:rarebreed/rhsm-qe.git
cd rhsm-qe 
git checkout stoner
```

You will also need to configure polarize in order to get all the annotations from rhsm-qe.  Create a file 
called xml-config.xml and put it in ~/.polarize.  There is a _skeleton_ config file that can be used from
the polarize project that is located here