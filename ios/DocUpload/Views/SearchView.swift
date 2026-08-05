import SwiftUI

struct SearchView: View {
    @EnvironmentObject private var session: SessionStore
    @Binding var path: [Route]

    @State private var appNumber = ""
    @State private var errorMessage: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Enter the application number to continue.")
                .foregroundStyle(.secondary)

            VStack(alignment: .leading, spacing: 6) {
                TextField("e.g. HDFC1023", text: $appNumber)
                    .textFieldStyle(.roundedBorder)
                    .textInputAutocapitalization(.characters)
                    .onChange(of: appNumber) { newValue in
                        let cleaned = newValue.filter { $0.isLetter || $0.isNumber }
                        if cleaned != newValue { appNumber = cleaned }
                        errorMessage = nil
                    }
                if let errorMessage {
                    Text(errorMessage).font(.footnote).foregroundStyle(.red)
                }
            }

            Button {
                let trimmed = appNumber.trimmingCharacters(in: .whitespacesAndNewlines)
                if trimmed.isEmpty {
                    errorMessage = "Application Number is required."
                } else {
                    path.append(.upload(trimmed))
                }
            } label: {
                Text("Search").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)

            Spacer()
        }
        .padding()
        .navigationTitle("Search Application")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Logout") { session.logout() }
            }
        }
    }
}
