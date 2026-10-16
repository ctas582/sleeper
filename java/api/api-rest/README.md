Sleeper REST API
================

The Sleeper REST API runs as a lambda behind an API Gateway HTTP API when deployed, but can be run locally too.

## Running it locally

The API serves one Sleeper instance, and needs to be told which.

Either name the bucket holding the instance configuration:

```sh
export SLEEPER_CONFIG_BUCKET=sleeper-<instance-id>-config-<account-id>
```

Or name the instance, and let the API work out the bucket:

```sh
export SLEEPER_INSTANCE_ID=<instance-id>
export SLEEPER_ACCOUNT_NAME=<account-id>
```

You will also need AWS credentials that can read the instance's configuration bucket, and a region, either from
`AWS_REGION` or from your AWS profile.

```sh
mvn quarkus:dev -pl api/api-rest
```

## Checking it is working

The health check does not require authentication:

```sh
curl localhost:8080/q/health
```

Neither does the endpoint reporting information about how to authenticate:

```sh
curl localhost:8080/api/v1/auth/info
```

Everything else is refused unless the client has authenticated and been granted access, so expect `401` error status codes until an authentication source is configured for the instance and a principal is granted the permission to a particular resource:

```sh
curl -i localhost:8080/api/v1/instance/properties
```

## Calling the API using credentials for an AWS IAM role

Make sure requests are sent to the API's IAM endpoint, which is the value of `sleeper.rest.api.iam.url`.

`CallApiWithIamCredentials` in the tests shows how to make a request, and can be run against a deployed instance:

```sh
mvn compile exec:java -pl api/api-rest -Dexec.classpathScope=test \
  -Dexec.mainClass=sleeper.api.rest.example.CallApiWithIamCredentials \
  -Dexec.args="https://<api-id>.execute-api.<region>.amazonaws.com/api/v1/instance/properties"
```

For a quick check by hand, `awscurl` signs a request the same way:

```sh
awscurl --service execute-api --region <region> \
  "https://<api-id>.execute-api.<region>.amazonaws.com/api/v1/instance/properties"
```

## Authorisation

Authenticating establishes only who a client is. What they may do comes from the principal allow-list, never from a
claim in the token they present.

Use the instance property `sleeper.rest.api.auth.admin.principals` to grant an initial administrator. The API can then be used to grant access to others.
