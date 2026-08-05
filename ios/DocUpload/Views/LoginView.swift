import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var session: SessionStore

    @State private var username = ""
    @State private var password = ""
    @State private var errorMessage: String?
    @State private var isSubmitting = false

    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            Text("🏦").font(.system(size: 56))
            Text("Welcome Back").font(.title2.weight(.bold))
            Text("Sign in to continue").foregroundStyle(.secondary)

            VStack(spacing: 14) {
                TextField("Username", text: $username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)
                SecureField("Password", text: $password)
                    .textFieldStyle(.roundedBorder)

                if let errorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }

                Button {
                    submit()
                } label: {
                    if isSubmitting {
                        ProgressView().frame(maxWidth: .infinity)
                    } else {
                        Text("Login").frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(isSubmitting)
            }
            .padding(.horizontal, 32)

            Text("Demo — Username: **hdfc** • Password: **123**")
                .font(.footnote)
                .foregroundStyle(.secondary)

            Spacer()
        }
        .padding()
    }

    private func submit() {
        errorMessage = nil
        guard !username.trimmed.isEmpty else { errorMessage = "Username is required."; return }
        guard !password.isEmpty else { errorMessage = "Password is required."; return }

        isSubmitting = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            isSubmitting = false
            if let error = session.login(username: username.trimmed, password: password) {
                errorMessage = error
            }
        }
    }
}

private extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
