#!/bin/bash
# Script de deploy do projeto Radar para o GitHub
set -e

REPO="$HOME/radar-app"
SRC="$HOME/Radar"  # onde os arquivos foram gerados pelo Claude

echo "📁 Copiando estrutura do projeto..."
cp -r "$SRC/"* "$REPO/"

echo "📤 Fazendo push para o GitHub..."
cd "$REPO"
git add -A
git commit -m "feat: projeto Radar completo - v1.0"
git push origin main

echo "✅ Pronto! Acesse o Codemagic para iniciar o build."
