# Loblaw Apigee Hybrid Installation Notes
**Table of Contents**
1. [Overview](#1-overview)
2. [Prerequisites](#2-Prerequisites)
3. [Connectivity Tests](#3-Connectivity-Tests)
4. [Create Apigee Project, Organization and Environment](#4-Create-Apigee-Project-Organization-and-Environment)
5. [Install the apigectl command](#5-Install-the-apigectl-command)
6. [Overriding of docker image.url.](#6-Overriding-of-docker-image-url)
7. [Update overrides/overrides.yaml file](#7-Update-overrides/overridesyaml-file)
8. [Install and configure Apigee Connect](#8-Install-and-configure-Apigee-Connect)
9. [Supply imagePullSecrets in config/values.yaml](#9-Supply-imagePullSecrets-in-config/valuesyaml)
10. [Create the imagePullSecret in every namespace.](#10-Create-the-imagePullSecret-in-every-namespace.)
11. [Configure imagePullSecrets on all istio-system and apigee-resources-install](#11-Configure-imagePullSecrets-on-all-istio-system-and-apigee-resources-install)	
12. [Run apigee init using the fixed yaml file](#12-Run-apigee-init-using-the-fixed-yaml-file)
13. [Complete and verify the installation](#13-Complete-and-verify-the-installation)
14. [Enable Synchronizer Access](#14-Enable-Synchronizer-Access)
15. [Test the installation using the Anthos Cluster Istio Load Balancer](#15-Test-the-installation-using-the-Anthos-Cluster-Istio-Load-Balancer)
16. [Add another Apigee Hybrid run-time plane for Anthos](#16-Add-another-Apigee-Hybrid-run-time-plane-for-anthos)
17. [Appendix A - Installer Roles](#17-Appendix-A---Installer-Roles)
18. [Appendix B - Namespace Patching Permissions](#18-Appendix-B---Namespace-Patching-Permissions)
19. [Appendix C - overrides.yaml file](#19-Appendix-C---overrides.yaml-file)
20. [Appendix D - Google Internal Issue Summary](#20-Appendix-D---Google-Internal-Issue-Summary)
* * *
##  1. Overview
This document describes the installation of Apigee Hybrid on a Loblaw Anthos Environment
The official [Google Reference Apigee Installation Documentation](https://docs.apigee.com/hybrid/v1.2/precog-overview) was followed for the installation. Deviations and peculiarities of Loblaw environments have been recorded below.
##  2. Prerequisites
1. A GCP project must be available (for DEV this is *lt-aap-dev*)
1. Submit a request to Google to whitelist the GCP project for a PAID Apigee org
1. Submit a request to Google to whitelist the GCP project for Apigee Connect.
1. A paid Google account for the person who will be installing Apigee Hybrid with appropriate permisions ([see Appendix A](#appendix-a---installer-roles)) must be available and setup. This account will be used to install Apigee Hybrid in the Loblaw environment
2. An Anthos cluster must be avaliable and accessible to the installer
3. The Apigee SaaS environment must be available and accessible to the installer
4. A Linux based Administration Workstation with Google Cloud SDK, kubectl and docker installed must be available and accessible to the installer
5. Google Cloud SDK and kubectl must be installed on a local Windows / Mac workstation or laptop. This is required to generate the Anthos cluster configuration
6. The apigeectl installation directory (/app) must be created with read/write access to the installer account.
7. The CA public certificate chain bundle (oidc-ca-certs.pem) supplied by Google for the Anthos installation must be available
8. Change the anthos cluster anthos-config-sync/cluster/resources.yaml file so that apigee admins can patch the apigee namespaces (see [Appendix B](#appendix-b---namespace-patching-permissions))
##  3. Connectivity Tests
###  3.1. GCP Console
1. Log in to the GCP Console with the Loblaw supplied Google account and verify that you can view the Kubernetes clusters **heathdv-cluster01** and **xheathdv-cluster01**  under project **lt-ath-lwr** 
2. Login to [Apigee UI](https://apigee.google.com/edge) and verify that you can access the Apigee home page
###  3.2. Laptop / Desktop
1. Check that you can login to google from their laptop / desktop.
    ```bash
    gcloud auth login
    ```
2. Check that you can connect to the Anthos cluster
    * Copy the kubectl-anthos-config-[*clusterName*].yaml file to the current directory
    * Connect to the anthos cluster
        ```bash
        gcloud anthos auth login --cluster xheathdv-cluster01 --login-config xheathdv-kubectl-anthos-config.yaml --kubeconfig config
        ```
    * Browse to http://localhost:8403/login and login with your Loblaw Google credentials
    * The Anthos kubeconfig file will be generated
###  3.3. Admin Workstation
1. Login to the Admin Workstation and copy the kubeconfig and the oidc-ca-certs.pem file above to ~/.kube/config. If the .kube folder does not exist create it. Modify the kubeconfig file to add the following line for the auth-provider of each cluster under *config*.
   ```bash
   idp-certificate-authority: /home/<userID>/.kube/oidc-ca-certs.pem
   ```
2. Setup the environment variables. It may be a good idea to add these to your .profile file
   ```bash
   export http_proxy=http://162.53.103.195:80
   export https_proxy=http://162.53.103.195:80
   export HTTP_PROXY=http://162.53.103.195:80
   export HTTPS_PROXY=http://162.53.103.195:80
   export NO_PROXY 10.0.0.0/8
   export KUBECONFIG=/home/jbiswas/.kube/config
   ```
3. Set the Google Anthos project
    ```bash
    gcloud config set project lt-ath-lwr
    ```
4. Set the Kubernetes context
   ```bash
   kubectl config use-context xheathdv-cluster01-xheathdv-cluster01-anthos-default-user
   ```
5. Check that you can connect to the anthos cluster where Apigee Hybrid is required to be installed
   ```bash
   kubectl --kubeconfig get nodes --output wide
   ```
6. Check that you can log in to the Loblaw private docker registry, *nexus*
   ```bash
   docker login nexus.repo.loblaw.ca:8086
   ```
##  4. Create Apigee Project, Organization and Environment
1. Login to the Apigee Edge UI and create the Apigee project as described in the documentation
2. Create an Apigee Organization
   ```bash
   gcloud config set project lt-aap-dev
    
   TOKEN=$(gcloud auth print-access-token)

   curl -H "Authorization: Bearer $TOKEN" -X POST -H "content-type:application/json"  -d '{
    "name":"hyb-dev",
    "displayName":"hyb-dev",
    "description":"Loblaw Apigee Dev ",
    "analyticsRegion":"us-east1"}' "https://apigee.googleapis.com/v1/organizations?parent=projects/lt-aap-dev"
    
   # Check that the Org was created successfully
   curl -H "Authorization: Bearer $TOKEN" "https://apigee.googleapis.com/v1/organizations?parent=projects/lt-aap-dev"
   ```
3. From the UI, create an Apigee environment named *ext-it*
##  5. Install the apigectl command
Download and install the apigeectl utility as described in the Google official documentation above. From here on all Apigee Hybrid installation commands should be run from the *hybrid-files* directory.
##  6. Overriding of docker image url
Due to the inability to pull images from docker.io through the corporate proxy the on-premises nexus registry should be used to pull from docker.io. This requires changing the image names in the overides/overides.yaml file for all of the images. The list of images is available in the online [documentation](https://docs.apigee.com/hybrid/v1.2/signed-docker-images). However,  a request has been submitted to Google to streamline the Apigee Hybrid registry configuration [(See Appendix D - issue 154241837)](#17-Appendix-D---Google-Internal-Issue-Summary).
## 7. Update overrides/overrides.yaml file
Update the following keys with values created in the above steps:
1. projectID
1. org
1. k8sCluster
1. virtualhosts
1. envs
1. ingress:
   - enableAccesslog: true
   - runtime:
     - loadBalancerIP: 162.53.255.212 (Static IP assigned by Loblaw Networking team for API ingress)
1. cassandra:
   - hostNetwork: true
   - dnsPolicy: ClusterFirstWithHostNet

See [Appendix C](#appendix-c---overridesyaml-file) for the complete overrides.yaml file.
## 8. Install and configure Apigee Connect
Follow the [Google documentation](https://docs.apigee.com/hybrid/v1.2/apigee-connect?hl=en) to install and configure Apigee Connect which allows the Apigee SaaS management plane to connect securely to the MART service in the Anthos runtime plane without requiring to expose the MART endpoint on the internet.

In Step #5 do NOT include the following key
  - replicaCountMin: 3
## 9. Supply imagePullSecrets in config/values.yaml
It&#39;s not possible to supply the imagePullSecrets value in overides/overides.yaml. The value is never picked up as it appends to a list rather then overriding the first value in the list. The only solution is to directly edit config/values.yaml and change the imagePullSecrets value there to be: 
```yaml
imagePullSecrets:
- name: regcred
```
[(See Appendix D - issue no 154256656)](#appendix-d---google-internal-issue-summary)
##  10. Create the imagePullSecret in every namespace.
There are four namespaces which require the nexus credentials be created in. This isn&#39;t done by the apigee-hybrid install and must be run prior to running appctl init.
```bash
kubectl create secret generic regcred --from-file=.dockerconfigjson=$HOME/.docker/config.json --type=kubernetes.io/dockerconfigjson -n cert-manager

kubectl create secret generic regcred --from-file=.dockerconfigjson=$HOME/.docker/config.json --type=kubernetes.io/dockerconfigjson -n apigee

kubectl create secret generic regcred --from-file=.dockerconfigjson=$HOME/.docker/config.json --type=kubernetes.io/dockerconfigjson -n apigee-system

kubectl create secret generic regcred --from-file=.dockerconfigjson=$HOME/.docker/config.json --type=kubernetes.io/dockerconfigjson -n istio-system 
```
##  11. Configure imagePullSecrets on all istio-system and apigee-resources-install
Apigeectl init does not pull image secrets in istio-system and the apigee-resource-install job in the apigee-system namespace. To correct this problem use [kustomize](https://github.com/kubernetes-sigs/kustomize) to add imagePullSecret properties to the serviceAccounts configured by apigee. 
[(See Appendix D - issue no 154245816)](#appendix-d---google-internal-issue-summary)
###  Generate the customized *apigee-init.yaml* file
####  11.1 Generate the apigee-init.yaml file
```bash
$APIGEECTL_HOME/apigeectl init -f overrides/overrides.yaml --print-yaml --dry-run > apigee-init.yaml
```
####  11.1 Download kustomize
```bash
curl -s "https://raw.githubusercontent.com/kubernetes-sigs/kustomize/master/hack/install_kustomize.sh"
```
####  11.1 Create the kustomization.yaml file in the current directory
```yaml
bases:
- apigee-init.yaml
patches:
- patch: |-
    kind: ignored
    metadata:
      name: ignored
    imagePullSecrets:
    - name: regcred
  target:
    kind: ServiceAccount
EOF
```
####  11.1 Run kustomize
```bash
chmod +x kustomize
mkdir -p  ~/bin/
cp kustomize ~/bin
export PATH=$HOME/bin:$PATH
kustomize build .  > apigee-init-fixed.yaml
```
##  12. Run apigee init using the fixed yaml file

```bash
kubectl apply -f apigee-init-fixed.yaml
$APIGEECTL_HOME/apigeectl check-ready -f overrides/overrides.yaml
```
##  13. Complete and verify the installation
The installation process will create the Cassandra Data Center (DC-1)
```bash
$APIGEECTL_HOME/apigeectl apply -f overrides/overrides.yaml

$APIGEECTL_HOME/apigeectl check-ready -f overrides/overrides.yaml

kubectl get pods -n apigee-system

kubectl get pods -n istio-system
```
##  14. Enable Synchronizer Access
```bash
export GOOGLE_APPLICATION_CREDENTIALS=service-accounts/lt-aap-dev-apigee-org-admin.json

export TOKEN=$(gcloud auth application-default print-access-token)
 
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type:application/json" \
  "https://apigee.googleapis.com/v1/organizations/hyb-dev:setSyncAuthorization" \
   -d '{"identities":["serviceAccount:apigee-synchronizer@lt-aap-dev.iam.gserviceaccount.com"]}'

# Verify Success
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type:application/json" \
    "https://apigee.googleapis.com/v1/organizations/your_org_name:getSyncAuthorization" \
   -d ''
```
##  15. Test the installation using the Anthos Cluster Istio Load Balancer
Using the UI, create a simple API Proxy named *testapi2* that validates and API Key and responds with a 'Hello World' message.

Deploy the API Proxy to the *ext-lt* environment

From the Admin Workstation invoke the following commands to test the API Proxy.
```bash
# Test without an API Key
curl https://iapi-dev.loblaw.ca/testapi2 -H "Host: iapi-dev.loblaw.ca" -v -k --resolve iapi-dev.loblaw.ca:443:162.53.255.212 --noproxy "*" --http1.1
# Test with a valid API key. Get the API key from the UI under the Apps menu
curl https://iapi-dev.loblaw.ca/testapi2?apikey=<Valid Api Key> -H "Host: iapi-dev.loblaw.ca" -v -k --resolve iapi-dev.loblaw.ca:443:162.53.255.212 --noproxy "*" --http1.1
# Test with an invalid API key
curl https://iapi-dev.loblaw.ca/testapi2?apikey=wrongApiKey -H "Host: iapi-dev.loblaw.ca" -v -k --resolve iapi-dev.loblaw.ca:443:162.53.255.212 --noproxy "*" --http1.1

```
## 16. Add another Apigee Hybrid run-time plane for Anthos
Apigee Hybrid allows multiple run-time planes to be attached to the same management planes. Follow the [Google documentation](https://docs.apigee.com/hybrid/v1.2/multi-region) to install an additional run-time plane to the management plane created above.

1. Pre-requisites
   - A new Anthos cluster with appropriate namespaces must be available for the new run-time plane
   - Since the clusters of the run-time planes communicate with one-another via Cassandra, the Cassandra ports 7000 and 7001 must be opened across all clusters
   - Do not configure the multi-region seed host
1. Copy existing overrides.yaml and rename to overrides-dc2.yaml
1. Edit the overrides-dc2.yaml file as follows:
   - Update env to the new environment to use.
   - Update virtual host
3. Using the same service accounts, configuration,  secrets and commands as for DC-1, carry out step 2 in the [documentation](https://docs.apigee.com/hybrid/v1.2/multi-region#set-up-the-new-region) (apigeectl init and apply commands).
5. Carry out the remaining steps. For step 3d, run the command for each entry from step 3c that does not reference dc-2
6. Do not carry out Step 6. 
7. Check the Cassandra cluster status as described in the [documentation](https://docs.apigee.com/hybrid/v1.2/multi-region#check-the-cassandra-cluster-status).
8. Test the new installation (TBD)


## 17. Appendix A - Installer Roles
**Apigee Project**
* Apigee Organization Admin
* Role Administrator
* Service Account Admin
* Service Account Key Admin
* Service Account User
* Logs Viewer
* Monitoring Metric Writer
* Monitoring Viewer
* Project IAM Admin
* Quota Administrator
* Stackdriver Accounts Editor

**Anthos Project**

* Apigee Organization Admin
* Compute Admin
* Kubernetes Engine Admin
* Kubernetes Engine Cluster Admin
* Role Administrator
* Service Account Admin
* Service Account Key Admin
* Service Account User
* Logs Viewer
* Monitoring Metric Writer
* Monitoring Viewer
* Project IAM Admin
* Quota Administrator
* Stackdriver Accounts Editor

##  18. Appendix B - Namespace Patching Permissions
File name: anthos-config-sync/cluster/resources.yaml
```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: apigee-namespace-roles
rules:
- apiGroups:
  - 'apigee.cloud.google.com'
  +  - 'cert-manager.io'
  +  - 'networking.istio.io'
  +  - 'authentication.istio.io'
  +  - 'config.istio.io'
  resources:
  - '*'
  verbs:
  - '*'
  resourceNames:
  - 'apigee'
  - 'apigee-system'
  - 'cert-manager'
  - 'istio-system'
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: apigee-namespace-roles
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: apigee-namespace-roles
subjects:
- apiGroup: rbac.authorization.k8s.io
  kind: User
  name: max.ha@loblaw.ca
- apiGroup: rbac.authorization.k8s.io
  kind: User
  name: jyoti.biswas@loblaw.ca
- apiGroup: rbac.authorization.k8s.io
  kind: User
  name: george.elkhouri2@loblaw.ca
- apiGroup: rbac.authorization.k8s.io
  kind: User
  name: vishwanath.puranik@loblaw.ca
- apiGroup: rbac.authorization.k8s.io
  kind: User
  name: rituraj.mishra@loblaw.ca
```
##  19. Appendix C - overrides.yaml file
```yaml
#
# This sample is ideal for a quick start. It uses the minimum recommended footprint to start apigee runtime components.
# As much as possible, the overrides uses default settings and values. All the minimum replicas are set 1
#

# GCP project name where the org is provisioned.
gcp:
  projectID: lt-aap-dev
# Apigee org name.
org: hyb-dev
# Kubernetes cluster name details
k8sCluster:
  name: heathdv-cluster01
  region: "us-east1"

virtualhosts:
  - name: default
    hostAliases: ["iapi-dev.loblaw.ca"]
    # either SSLSecret or the paths
    # sslSecret: "test-sslsecret"
    # Certificate for the domain name; this can be self signed.
    sslCertPath: ./certs/iapi-dev.loblaw.ca-cert.pem
    # Private key for the domain name; this can be self signed.
    sslKeyPath: ./certs/iapi-dev.loblaw.ca-cert.key
    # optional
    # additionalGateways: ["name of the istio gateway, if in different ns, then ns/gatewayname"]
    routingRules:
      - env: ext-lt
        # base paths. Omit this if the base path is "/"
        # paths:
        #  - /testapi2
        #  - /v1/customers2
        # optional, connect timeout in seconds
        # connectTimeout: 57
      


envs:
    # Apigee environment name.
  - name: ext-lt
    # Service accounts for sync and UDCA.
    serviceAccountPaths:
      synchronizer: ./service-accounts/lt-aap-dev-apigee-synchronizer.json
      udca: ./service-accounts/lt-aap-dev-apigee-udca.json
  #- name: test
    # Service accounts for sync and UDCA.
    #serviceAccountPaths:
      #synchronizer: ./service-accounts/lt-aap-dev-apigee-synchronizer.json
      #udca: ./service-accounts/lt-aap-dev-apigee-udca.json

mart:
  hostAlias: "iapi-dev.loblaw-mart.ca"
  serviceAccountPath: ./service-accounts/lt-aap-dev-apigee-mart.json
  sslCertPath: ./certs/iapi-dev.loblaw.ca-cert.pem
  sslKeyPath: ./certs/iapi-dev.loblaw.ca-cert.key
  image:
    url: "nexus-repo.loblaw.ca:8086/google/apigee-mart-server"

metrics:
  serviceAccountPath: ./service-accounts/lt-aap-dev-apigee-metrics.json
  prometheus:
    image:
      url: "nexus-repo.loblaw.ca:8086/google/apigee-prom-prometheus"
  sdSidecar:
    image:
      url: "nexus-repo.loblaw.ca:8086/google/apigee-stackdriver-prometheus-sidecar"

cassandra:
  replicaCount: 3
  auth:
    image:
      url: "nexus-repo.loblaw.ca:8086/google/apigee-hybrid-cassandra-client"
  image:
    url: "nexus-repo.loblaw.ca:8086/google/apigee-hybrid-cassandra"
  backup:
    image:
      url: "nexus-repo.loblaw.ca:8086/google/apigee-cassandra-backup-utility"
  restore:
    image:
      url: "nexus-repo.loblaw.ca:8086/google/apigee-cassandra-backup-utility"

ingress:
  enableAccesslog: true
  runtime:
    loadBalancerIP: 162.53.255.212
  mart:
    loadBalancerIP: x.x.x.x

httpProxy:
  host: 162.53.199.228
  port: 80
  scheme: HTTP

logger:
  image:
    url: "nexus-repo.loblaw.ca:8086/google/apigee-stackdriver-logging-agent"

istio:
  pilot:
    image:
      url: "nexus-repo.loblaw.ca:8086/google/apigee-istio-pilot"
  kubectl:
    image:
      url: "nexus-repo.loblaw.ca:8086/google/apigee-istio-kubectl"
  galley:
    image:
      url: "nexus-repo.loblaw.ca:8086/google/apigee-istio-galley"
  node_agent_k8s:
    image:
      url: "nexus-repo.loblaw.ca:8086/google/apigee-istio-node-agent-k8s"
  proxyv2:
    image:
      url: "nexus-repo.loblaw.ca:8086/google/apigee-istio-proxyv2"
  mixer:
    image:
      url: "nexus-repo.loblaw.ca:8086/google/apigee-istio-mixer"
  citadel:
    image:
      url: "nexus-repo.loblaw.ca:8086/google/apigee-istio-citadel"
  sidecar_injector:
    image:
      url: "nexus-repo.loblaw.ca:8086/google/apigee-istio-sidecar-injector"
 

certmanager:
  image:
    url: "nexus-repo.loblaw.ca:8086/google/apigee-cert-manager-controller" 

certmanagerwebhook:
  image:
    url: "nexus-repo.loblaw.ca:8086/google/apigee-cert-manager-webhook" 

certmanagercainjector:
  image:
    url: "nexus-repo.loblaw.ca:8086/google/apigee-cert-manager-cainjector" 

authz:
  image:
    url: "nexus-repo.loblaw.ca:8086/google/apigee-authn-authz" 

synchronizer:
  image:
    url: "nexus-repo.loblaw.ca:8086/google/apigee-synchronizer" 

runtime:
  image:
    url: "nexus-repo.loblaw.ca:8086/google/apigee-runtime" 

udca:
  image:
    url: "nexus-repo.loblaw.ca:8086/google/apigee-udca"
  fluentd:
    image:
      url: "nexus-repo.loblaw.ca:8086/google/apigee-stackdriver-logging-agent" 

connectAgent:
  image:
    url: "nexus-repo.loblaw.ca:8086/google/apigee-connect-agent"
  enabled: true
  serviceAccountPath: ./service-accounts/lt-aap-dev-apigee-mart.json 

ao:
  image:
    url: "nexus-repo.loblaw.ca:8086/google/apigee-operators" 

kubeRBACProxy:
  image:
    url: "nexus-repo.loblaw.ca:8086/google/apigee-kube-rbac-proxy"
```
##  20. Appendix D - Google Internal Issue Summary

**154256656** no imagePullSecrets configured on all istio-system and apigee-resources-install

**154245816** values.yaml shouldn&#39;t define a default value for imagePullSecrets

**154241837** overriding apigee-hybrid registry takes lots of configurations, make it a top level configuration value

**154235685** list of apigee-hybrid images aren&#39;t complete or correct.