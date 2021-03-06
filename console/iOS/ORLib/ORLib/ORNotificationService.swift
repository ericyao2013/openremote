/*
 * Copyright 2017, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

import UserNotifications

open class ORNotificationService: UNNotificationServiceExtension, URLSessionDelegate {

    public var contentHandler: ((UNNotificationContent) -> Void)?
    public var bestAttemptContent: UNMutableNotificationContent?

    open override func didReceive(_ request: UNNotificationRequest, withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void) {
        self.contentHandler = contentHandler
        bestAttemptContent = (request.content.mutableCopy() as? UNMutableNotificationContent)
        NSLog("NotifExtension Change content : %@ ", bestAttemptContent?.userInfo ?? "")
        if let bestAttemptContent = bestAttemptContent {
            // Call backend to get payload and adapt title, body and actions
            let defaults = UserDefaults(suiteName: ORAppGroup.entitlement)

            defaults?.synchronize()

            guard let tkurlRequest = URL(string:String(format: "\(ORServer.scheme)://%@/auth/realms/%@/protocol/openid-connect/token", ORServer.hostURL, ORServer.realm))
                else { return }
            let tkRequest = NSMutableURLRequest(url: tkurlRequest)
            tkRequest.addValue("application/x-www-form-urlencoded", forHTTPHeaderField:"Content-Type");
            tkRequest.httpMethod = "POST"
            let postString = String(format: "grant_type=refresh_token&refresh_token=%@&client_id=openremote", defaults!.object(forKey: DefaultsKey.refreshToken) as! CVarArg)
            tkRequest.httpBody = postString.data(using: .utf8)
            let sessionConfiguration = URLSessionConfiguration.default
            let session = URLSession(configuration: sessionConfiguration, delegate: self, delegateQueue: nil)
            let req = session.dataTask(with: tkRequest as URLRequest, completionHandler: { (data, response, error) in
                if (data != nil) {
                    do {
                        let jsonDictionnary: Dictionary = try JSONSerialization.jsonObject(with: data!, options: []) as! [String:Any]
                        if ((jsonDictionnary["access_token"]) != nil) {
                            guard let urlRequest = URL(string: ORServer.apiTestResource) else { return }
                            let request = NSMutableURLRequest(url: urlRequest)
                            request.addValue(String(format:"Bearer %@", jsonDictionnary["access_token"] as! String), forHTTPHeaderField: "Authorization")
                            let sessionConfiguration = URLSessionConfiguration.default
                            let session = URLSession(configuration: sessionConfiguration, delegate: self, delegateQueue: nil)
                            let reqDataTask = session.dataTask(with: request as URLRequest, completionHandler: { (data, response, error) in
                                DispatchQueue.main.async {
                                    if (error != nil) {
                                        bestAttemptContent.body = error.debugDescription
                                    } else {
                                        do {
                                            //print(String(data: data!, encoding: .utf8) ?? "")
                                            let json = try JSONSerialization.jsonObject(with: data!) as? [[String: Any]]
                                            if (json?.count)! > 0 {
                                                let categoryName = "openremoteNotification"
                                                let detailedJson = (json?[0])! as [String: Any]
                                                bestAttemptContent.categoryIdentifier = categoryName
                                                bestAttemptContent.title = detailedJson["title"] as! String
                                                bestAttemptContent.body = detailedJson["message"] as! String
                                                bestAttemptContent.userInfo["appUrl"] = detailedJson["appUrl"]
                                                bestAttemptContent.userInfo["alertId"] = detailedJson["id"]
                                                let actions = detailedJson["actions"] as! [[String : Any]]
                                                var notificationActions = [UNNotificationAction]()
                                                for i in (0..<actions.count) {
                                                    let actionTitle = actions[i]["title"]! as! String
                                                    let actionType = actions[i]["type"]! as! String
                                                    switch actionType {
                                                    case ActionType.ACTION_ACTUATOR :
                                                        bestAttemptContent.userInfo["actions"] = actions[i]
                                                        notificationActions.append(UNNotificationAction(identifier: actionType, title: actionTitle, options: UNNotificationActionOptions.destructive))
                                                    case ActionType.ACTION_DEEP_LINK :
                                                        notificationActions.append(UNNotificationAction(identifier: actionType, title: actionTitle, options: UNNotificationActionOptions.foreground))
                                                    default : break
                                                    }
                                                }
                                                let category = UNNotificationCategory(identifier: categoryName, actions: notificationActions, intentIdentifiers: [], options: [])
                                                let categories : Set = [category]
                                                UNUserNotificationCenter.current().setNotificationCategories(categories)
                                            } else {
                                                bestAttemptContent.body = "could not deserialize JSON"
                                            }
                                        } catch  {
                                            bestAttemptContent.body = "could not deserialize JSON"
                                        }
                                        contentHandler(bestAttemptContent)
                                    }
                                }
                            })
                            reqDataTask.resume()
                        } else {
                            if let httpResponse = response as? HTTPURLResponse {
                                let error = NSError(domain: "", code: httpResponse.statusCode, userInfo: jsonDictionnary)
                                bestAttemptContent.body = error.debugDescription
                                contentHandler(bestAttemptContent)
                            } else {
                                let error = NSError(domain: "", code: 0, userInfo: jsonDictionnary)
                                bestAttemptContent.body = error.debugDescription
                                contentHandler(bestAttemptContent)
                            }
                        }
                    }
                    catch let error as NSError {
                        bestAttemptContent.body = error.debugDescription
                        contentHandler(bestAttemptContent)
                    }
                } else {
                    bestAttemptContent.body = error.debugDescription
                    contentHandler(bestAttemptContent)
                }
            })
            req.resume()

        }
    }

    open override func serviceExtensionTimeWillExpire() {
        // Called just before the extension will be terminated by the system.
        // Use this as an opportunity to deliver your "best attempt" at modified content, otherwise the original push payload will be used.
        NSLog("NotifExtension Time has expired")
        if let contentHandler = contentHandler, let bestAttemptContent =  bestAttemptContent {
            bestAttemptContent.title = "You received an alarm from blok61 :"
            bestAttemptContent.body = "Please open application to check what's happening"
            contentHandler(bestAttemptContent)
        }
    }

    open func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge, completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        if (challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust) {
            if challenge.protectionSpace.host == ORServer.hostURL  {
                completionHandler(.useCredential, URLCredential(trust: challenge.protectionSpace.serverTrust!))
            } else {
                completionHandler(.performDefaultHandling,nil)
            }
        }
    }
}

