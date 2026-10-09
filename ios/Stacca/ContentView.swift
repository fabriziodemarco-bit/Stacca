import SwiftUI

struct ContentView: View {
    var body: some View {
        VStack(spacing: 12) {
            Text("Stacca!")
                .font(.largeTitle)
                .bold()
            Text("Basta lavorare. Vivi.")
                .foregroundStyle(.secondary)
        }
        .padding()
        .preferredColorScheme(.dark)
    }
}

#Preview {
    ContentView()
}
