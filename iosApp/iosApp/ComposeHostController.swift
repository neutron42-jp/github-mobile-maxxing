import UIKit
import ComposeApp

/// Hosts the Compose view controller as a child and owns the status bar style: Compose reports
/// the effective dark theme, and the status bar content colour follows it, including a Dark/Light
/// override that differs from the OS appearance.
final class ComposeHostController: UIViewController {
    private var isDark = false

    override var preferredStatusBarStyle: UIStatusBarStyle {
        isDark ? .lightContent : .darkContent
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        let compose = MainViewControllerKt.MainViewController(onDarkThemeChanged: { [weak self] dark in
            let apply = {
                guard let self else { return }
                let value = dark.boolValue
                if self.isDark != value {
                    self.isDark = value
                    self.setNeedsStatusBarAppearanceUpdate()
                }
            }
            if Thread.isMainThread { apply() } else { DispatchQueue.main.async(execute: apply) }
        })
        addChild(compose)
        compose.view.frame = view.bounds
        compose.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(compose.view)
        compose.didMove(toParent: self)
    }
}
