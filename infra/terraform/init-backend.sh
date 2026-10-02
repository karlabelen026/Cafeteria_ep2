#!/usr/bin/env bash
# Crea (si no existen) el bucket S3 y la tabla DynamoDB para el backend remoto
# de Terraform, y corre "terraform init" apuntando a ellos. Lo usan tanto
# GitHub Actions (.github/workflows/terraform-deploy.yml y
# terraform-destroy.yml) como vos en local: así el state nunca vive solo en
# el disco del runner (ver backend.tf) y "terraform apply"/"destroy" siempre
# parten del mismo estado, sin importar dónde se ejecuten.
#
# Requiere: AWS CLI configurado (variables AWS_ACCESS_KEY_ID /
# AWS_SECRET_ACCESS_KEY / AWS_SESSION_TOKEN de tu sesión de AWS Academy) y
# Terraform. Idempotente: si el bucket/tabla ya existen, no hace nada y solo
# corre "terraform init".
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
BUCKET="cafegestion360-tfstate-${ACCOUNT_ID}"
TABLE="cafegestion360-tfstate-lock"

echo "Backend remoto: bucket=$BUCKET tabla=$TABLE region=$REGION"

if ! aws s3api head-bucket --bucket "$BUCKET" --region "$REGION" 2>/dev/null; then
  echo "El bucket $BUCKET no existe, creandolo..."
  if [ "$REGION" = "us-east-1" ]; then
    aws s3api create-bucket --bucket "$BUCKET" --region "$REGION"
  else
    aws s3api create-bucket --bucket "$BUCKET" --region "$REGION" \
      --create-bucket-configuration LocationConstraint="$REGION"
  fi
  aws s3api put-bucket-versioning --bucket "$BUCKET" --versioning-configuration Status=Enabled
  aws s3api put-public-access-block --bucket "$BUCKET" --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
else
  echo "El bucket $BUCKET ya existe."
fi

if ! aws dynamodb describe-table --table-name "$TABLE" --region "$REGION" >/dev/null 2>&1; then
  echo "La tabla $TABLE no existe, creandola..."
  aws dynamodb create-table \
    --table-name "$TABLE" \
    --attribute-definitions AttributeName=LockID,AttributeType=S \
    --key-schema AttributeName=LockID,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region "$REGION"
  aws dynamodb wait table-exists --table-name "$TABLE" --region "$REGION"
else
  echo "La tabla $TABLE ya existe."
fi

terraform init -input=false \
  -backend-config="bucket=$BUCKET" \
  -backend-config="key=cafeteria/terraform.tfstate" \
  -backend-config="region=$REGION" \
  -backend-config="dynamodb_table=$TABLE" \
  -backend-config="encrypt=true"
