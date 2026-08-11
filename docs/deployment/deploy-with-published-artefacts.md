Deploy with published artefacts
===============================

Sleeper deployments normally build (or download from a Maven / Docker repository) all jars and Docker images, then upload
them into a per-instance S3 bucket and ECR namespace before running CDK. When deploying many instances of the same
tagged release into an account, that upload becomes waste — the artefacts are identical across every deployment.

This page describes an alternative flow where a release is published **once** to a shared S3 bucket and ECR registry
(potentially in a different AWS account), and every subsequent deployment references those artefacts without
re-uploading them.

## Overview

There are two roles:

- **Publisher**: builds a Sleeper release once and pushes it into a chosen S3 bucket and ECR registry, under a
  namespace prefix (a version number for tagged releases, or a branch name for snapshots).
- **Consumer**: deploys a Sleeper instance configured to read jars and images from that published location instead of
  building and uploading them.

The two roles can be in the same or different AWS accounts.

## Instance properties

The consumer side is controlled by these instance properties (see [`CommonProperty.java`](../../java/core/src/main/java/sleeper/core/properties/instance/CommonProperty.java)):

| Property | Purpose |
| --- | --- |
| `sleeper.artefacts.mode` | `build` (default) or `published`. In `published` mode, the deploy client skips jar sync and Docker image upload, and instead verifies that the referenced artefacts exist before running CDK. |
| `sleeper.jars.bucket` | The S3 bucket containing published jars. Must be set explicitly in published mode. |
| `sleeper.artefacts.prefix` | A shared namespace prefix within the jars bucket and within the ECR repository namespace. When set, jars are read at `s3://<jars.bucket>/<artefacts.prefix>/<jar>` and images from `<ecr.prefix>/<artefacts.prefix>/<image>`. |
| `sleeper.ecr.repository.prefix` | The ECR repository prefix. Must be set explicitly in published mode. |
| `sleeper.ecr.repository.account` | The AWS account hosting the release ECR. Defaults to the deploying account. Set this when the release ECR is in a different account. |
| `sleeper.ecr.repository.region` | The AWS region hosting the release ECR. Defaults to the deploying region. |

Note: `sleeper.userjars` (user-supplied iterator jars) are always read from the root of `sleeper.jars.bucket` and are
unaffected by `sleeper.artefacts.prefix`.

## Publishing a release

Build the Sleeper release locally so that `scripts/jars/` and the Docker images are present, then run:

```
scripts/dev/publishRelease.sh \
    sleeper-releases \
    111122223333.dkr.ecr.eu-west-2.amazonaws.com/sleeper \
    v1.0.0
```

This publishes:

- Every jar under `s3://sleeper-releases/v1.0.0/<jar>.jar`
- Every Docker image at `111122223333.dkr.ecr.eu-west-2.amazonaws.com/sleeper/v1.0.0/<image>:<version>`

`scripts/dev/publishJarsToS3.sh` and `scripts/dev/publishDocker.sh` can also be run independently if only one side needs
updating.

### Recommended prefix conventions

- **Tagged releases**: use the version number, e.g. `v1.0.0`. This keeps every release immutable and side-by-side in the
  same bucket / registry.
- **Branch snapshots**: use `branches/<branch>` to keep branches (which may share a `-SNAPSHOT` jar version) from
  colliding.
- **Nightly builds**: use `nightly/<date>`.

The rule of thumb is **one prefix per immutable artefact set**, so that a consumer that has pinned to a prefix will
never see it change under them.

### Cross-account IAM requirements

When the release account differs from the deploying account, both S3 and ECR must permit cross-account access.

**S3 bucket policy** on the release bucket:

```
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "AllowConsumerAccountRead",
    "Effect": "Allow",
    "Principal": { "AWS": "arn:aws:iam::<consumer-account>:root" },
    "Action": ["s3:GetObject", "s3:GetObjectVersion", "s3:ListBucket"],
    "Resource": [
      "arn:aws:s3:::sleeper-releases",
      "arn:aws:s3:::sleeper-releases/v1.0.0/*"
    ]
  }]
}
```

Narrower policies can grant only the specific prefix that a given consumer is allowed to read.

**ECR repository policy** on each Sleeper Docker repository in the release account:

```
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "AllowConsumerAccountPull",
    "Effect": "Allow",
    "Principal": { "AWS": "arn:aws:iam::<consumer-account>:root" },
    "Action": [
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchCheckLayerAvailability"
    ]
  }]
}
```

If the release S3 bucket is encrypted with a customer-managed KMS key, the consumer's Lambda role must additionally
have `kms:Decrypt` on that key.

### Optional: enable S3 versioning on the release bucket

CDK detects that a Lambda's code has changed by looking at the S3 object's version ID. If the release bucket has
versioning enabled, this works automatically and each redeploy against a re-published prefix will refresh the affected
Lambdas.

If the release bucket does not have versioning enabled, deploys still succeed but with these caveats:

- CDK emits a warning at synth time
  (`@aws-cdk/aws-lambda:codeFromBucketObjectVersionNotSpecified`) for each Lambda that references the release bucket.
- If someone republishes to the same `sleeper.artefacts.prefix`, a redeploy of an instance already pointing at that
  prefix will not update the Lambdas — CloudFormation sees no property change.

For tagged releases this doesn't matter because a tag is immutable. For branch snapshots either:

- Enable versioning on the release bucket, or
- Use a datestamped prefix per publish (e.g. `branches/feature-x/2026-08-05`), or
- Increment a snapshot suffix.

### Making tags immutable

Because Sleeper CDK deployments pin ECR image references to the release tag (rather than the digest, in `published`
mode), it is strongly recommended to set the release ECR repositories to `ImageTagMutability=IMMUTABLE`. Otherwise a
republish under the same tag will not be picked up until CDK is next re-run, and running deployments will silently
diverge from what is documented in the release.

## Consuming a published release

Set the following in your instance properties file when creating a new instance:

```
sleeper.artefacts.mode=published
sleeper.jars.bucket=sleeper-releases
sleeper.ecr.repository.prefix=sleeper
sleeper.artefacts.prefix=v1.0.0
# Only needed for cross-account:
sleeper.ecr.repository.account=111122223333
sleeper.ecr.repository.region=eu-west-2
```

Then deploy as usual (`scripts/deploy/deployNew.sh` / `scripts/deploy/deployExisting.sh`). The deploy client will:

1. Verify that every required jar exists in the referenced S3 bucket and that every required Docker image exists in the
   referenced ECR repositories at the declared version. If anything is missing, it fails fast with a list of the
   missing artefacts and exits before invoking CDK.
2. Skip `SyncJars` and `UploadDockerImages`.
3. Skip the auto-deployment of the artefacts CDK app (no per-instance jars bucket or ECR namespace is created).
4. Run the main CDK app, which references the published S3 objects and ECR repositories directly.

## Interaction with `sleeper.artefacts.deployment`

`sleeper.artefacts.deployment` remains the primary mechanism for sharing artefacts across instances **within the same
account**, and takes effect when `sleeper.artefacts.mode=build` (the default). It is ignored in `published` mode: in
that mode `sleeper.jars.bucket` and `sleeper.ecr.repository.prefix` are the authoritative pointers and must be set
explicitly.

## Limitations and gotchas

- The version of the local Sleeper deploy client (`clients-<version>-utility.jar`) must match the published version.
  The published artefacts are looked up by `sleeper.version`, which is set from the local jar.
- Once an instance is deployed in `published` mode, the property is not editable. Migrating an instance between `build`
  and `published` modes requires tearing down and re-deploying (the artefact locations move between per-instance and
  shared, so the underlying CloudFormation resources change too).
- The published-artefacts pre-check makes read-only S3/ECR API calls before invoking CDK. The deploying role therefore
  needs those permissions in addition to normal deploy permissions.
