# Contributing

Thank you for your interest in contributing to **AFFT-Toolkit**! 🎉

## Branch Strategy

- **`main`** — stable branch for releases.
- Create a **feature branch** from `main` for each change, then submit a Pull Request.

## How to Contribute

1. **Fork** this repository.
2. Create a new branch: `git checkout -b feat/your-feature`.
3. Make your changes.
4. Ensure code follows formatting standards (**ktlint**):
   ```bash
   ./gradlew :app:runKtlintCheckOverMainSourceSet
   ./gradlew ktlintFormat
   ```
   Note: `ktlintCheck` penuh (termasuk test source set) masih dalam
   proses perapian dan belum menjadi CI gate.
5. Commit with clear messages (use [Conventional Commits](https://www.conventionalcommits.org/)):
   ```
   feat: add partition editor
   fix: fix crash during payload.bin extraction
   docs: update README with new instructions
   ```
6. Push to your branch: `git push origin feat/your-feature`.
7. Open a **Pull Request** to the `main` branch.

## Code Guidelines

- Use **Kotlin** with consistent style matching existing code.
- UI follows existing patterns (Jetpack Compose + Material 3).
- Avoid magic numbers — use descriptive constants.
- New features should include documentation in README if relevant.

## Reporting Bugs

Open a [new issue](https://github.com/soe1hom-arch/AFFT-Toolkit/issues/new/choose) with the Bug Report template.

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
